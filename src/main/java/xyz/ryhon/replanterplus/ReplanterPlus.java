package xyz.ryhon.replanterplus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
//import net.minecraft.block.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class ReplanterPlus implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("Replanter");
	private static final Minecraft mc = Minecraft.getInstance();
	static Boolean useIgnore = false;

	public static Boolean enabled = true;
	public static Boolean sneakToggle = true;
	public static int useDelay = 4;
	public static Boolean missingItemNotifications = true;
	public static Boolean autoSwitch = true;
	public static Boolean requireSeedHeld = false;

	int ticks = 0;
	final int autoSaveTicks = 20 * 60 * 3;

	@Override
	public void onInitialize() {
		loadConfig();

		Category bindCategory = Category.register(Identifier.fromNamespaceAndPath("replanter", "replanter"));
		KeyMapping menuBind = new KeyMapping("key.replanter.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN,
				bindCategory);
		KeyMappingHelper.registerKeyMapping(menuBind);
		KeyMapping toggleBind = new KeyMapping("key.replanter.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN,
				bindCategory);
		KeyMappingHelper.registerKeyMapping(toggleBind);

		ClientTickEvents.END_CLIENT_TICK.register((client) -> {
			ticks++;
			if (ticks == autoSaveTicks) {
				ticks = 0;
				saveConfig();
			}

			if (menuBind.consumeClick())
				client.setScreenAndShow(new ConfigScreen(null));

			if (toggleBind.consumeClick())
				enabled = !enabled;
		});

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (player instanceof ServerPlayer || useIgnore)
				return InteractionResult.PASS;

			if (!enabled || (sneakToggle && player.isShiftKeyDown()))
				return InteractionResult.PASS;

			LocalPlayer p = (LocalPlayer) player;
			BlockState state = world.getBlockState(hitResult.getBlockPos());

			if (state.getBlock() instanceof CocoaBlock cb) {
				if (!cb.isValidBonemealTarget(world, hitResult.getBlockPos(), state)) {
					breakAndReplantCocoa(p, state, hitResult);
					return InteractionResult.SUCCESS;
				} else {
					InteractionHand h = findAndEquipSeed(player, Items.BONE_MEAL);
					if (h != null) {
						useIgnore = true;
						mc.gameMode.useItemOn(p, h, hitResult);
						useIgnore = false;
						return InteractionResult.SUCCESS;
					}
				}
			} else if (isCrop(state)) {
				if (isGrown(state)) {
					breakAndReplant(p, hitResult);
					return InteractionResult.SUCCESS;
				} else {
					InteractionHand h = findAndEquipSeed(player, Items.BONE_MEAL);
					if (h != null) {
						useIgnore = true;
						mc.gameMode.useItemOn(p, h, hitResult);
						useIgnore = false;
						return InteractionResult.SUCCESS;
					}
				}
			}

			return InteractionResult.PASS;
		});
	}

	Boolean findInstamineTool(LocalPlayer p, BlockState state, BlockPos pos) {
		if (state.getDestroyProgress(p, p.level(), pos) >= 1f)
			return true;

		if (!autoSwitch)
			return false;

		int currentSlot = p.getInventory().getSelectedSlot();
		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			p.getInventory().setSelectedSlot(i);
			if (state.getDestroyProgress(p, p.level(), pos) >= 1f) {
				mc.gameMode.ensureHasSentCarriedItem();
				return true;
			}
		}
		p.getInventory().setSelectedSlot(currentSlot);

		return false;
	}

	Boolean isCrop(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof CropBlock)
			return true;
		else if (block instanceof NetherWartBlock)
			return true;
		else if (block instanceof PitcherCropBlock)
			return PitcherCropBlock.isLower(state);
		else if (block == Blocks.TORCHFLOWER || block == Blocks.TORCHFLOWER_CROP)
			return true;

		return false;
	}

	Boolean isGrown(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof CropBlock crop)
			return crop.isMaxAge(state);
		else if (block instanceof NetherWartBlock)
			return (Integer) state.getValue(NetherWartBlock.AGE) == 3;
		else if (block instanceof PitcherCropBlock pcb)
			// Interacting with upper half will reject the use packet
			// because it's too far away
			return pcb.isMaxAge(state) && PitcherCropBlock.isLower(state);
		if (block == Blocks.TORCHFLOWER)
			return true;

		return false;
	}

	void breakAndReplant(LocalPlayer player, BlockHitResult hit) {
		Item seed = getSeed(player.level().getBlockState(hit.getBlockPos()).getBlock());
		InteractionHand h = findAndEquipSeed(player, seed);
		if (requireSeedHeld && h == null) {
			sendMissingItemMessage(player, seed);
			return;
		}

		holdFortuneItem(player);
		mc.gameMode.startDestroyBlock(hit.getBlockPos(), hit.getDirection());

		if (h != null) {
			useIgnore = true;
			mc.gameMode.useItemOn(player, h, hit.withPosition(
					hit.getBlockPos()));
			useIgnore = false;
		} else
			sendMissingItemMessage(player, seed);
		//mc.rightClickDelay = useDelay;
	}

	void breakAndReplantCocoa(LocalPlayer p, BlockState state, BlockHitResult hitResult) {
		if (findInstamineTool(p, state, hitResult.getBlockPos())) {
			Item seed = state.getBlock().asItem();
			InteractionHand h = findAndEquipSeed(p, seed);

			if (requireSeedHeld && h == null) {
				sendMissingItemMessage(p, seed);
				return;
			}

			mc.gameMode.startDestroyBlock(hitResult.getBlockPos(), hitResult.getDirection());
			if (h != null) {
				Direction dir = (Direction) state.getValue(CocoaBlock.FACING);

				float x, y, z;
				x = dir.getStepX();
				y = dir.getStepY();
				z = dir.getStepZ();
				BlockHitResult placeHit = BlockHitResult.miss(
						hitResult.getLocation().add(x, y, z), dir.getOpposite(),
						hitResult.getBlockPos().offset(dir.getUnitVec3i()));

				useIgnore = true;
				mc.gameMode.useItemOn(p, h, placeHit);
				useIgnore = false;
			} else
				sendMissingItemMessage(p, seed);
			//mc.rightClickDelay = useDelay;
		}
	}

	Item getSeed(Block block) {
		if (block instanceof CropBlock cb) {
			return cb.asItem();
		} else if (block instanceof NetherWartBlock) {
			return Items.NETHER_WART;
		} else if (block instanceof PitcherCropBlock) {
			return Items.PITCHER_POD;
		} else if (block == Blocks.TORCHFLOWER) {
			return Items.TORCHFLOWER_SEEDS;
		}

		return null;
	}

	InteractionHand findAndEquipSeed(Player p, Item item) {
		if (item == null)
			return null;

		Inventory pi = p.getInventory();
		if (pi.getItem(pi.getSelectedSlot()).is(item))
			return InteractionHand.MAIN_HAND;
		if (pi.getItem(Inventory.SLOT_OFFHAND).is(item))
			return InteractionHand.OFF_HAND;

		if (!autoSwitch)
			return null;

		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			if (pi.getItem(i).is(item)) {
				pi.setSelectedSlot(i);
				mc.gameMode.ensureHasSentCarriedItem();
				mc.getConnection().send(new ServerboundPlayerActionPacket(
						ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
				return InteractionHand.OFF_HAND;
			}
		}
		return null;
	}

	void holdFortuneItem(Player p) {
		if(!autoSwitch)
			return;

		int maxLevel = 0;
		int slot = -1;

		Inventory pi = p.getInventory();
		Registry<Enchantment> enchantRegistry = p.level().registryAccess().lookup(Registries.ENCHANTMENT).get();
		Optional<Holder.Reference<Enchantment>> fortune = enchantRegistry.get(Enchantments.FORTUNE.identifier());
		// Server removed the Fortune enchantment????
		if (!fortune.isPresent())
			return;

		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			int lvl = EnchantmentHelper.getItemEnchantmentLevel(fortune.get(), pi.getItem(i));
			if (lvl > maxLevel) {
				maxLevel = lvl;
				slot = i;
			}
		}

		if (slot != -1) {
			pi.setSelectedSlot(slot);
			mc.gameMode.ensureHasSentCarriedItem();
		}
	}

	void sendMissingItemMessage(Player player, Item seed) {
		if (missingItemNotifications)
			player.sendSystemMessage(
					Component.translatable(seed.getDescriptionId())
							.append(Component.translatable(
									autoSwitch ? "replanter.gui.seed_not_in_hotbar" : "replanter.gui.seed_not_in_hand"))
							.setStyle(Style.EMPTY.withColor(0xFF0000)));
	}

	static Path configDir = FabricLoader.getInstance().getConfigDir().resolve("replanterplus");
	static Path configFile = configDir.resolve("config.json");

	static void loadConfig() {
		try {
			Files.createDirectories(configDir);
			if (!Files.exists(configFile))
				return;

			String str = Files.readString(configFile);
			JsonObject jo = (JsonObject) JsonParser.parseString(str);

			if (jo.has("enabled"))
				enabled = jo.get("enabled").getAsBoolean();
			if (jo.has("sneakToggle"))
				sneakToggle = jo.get("sneakToggle").getAsBoolean();
			if (jo.has("useDelay"))
				useDelay = jo.get("useDelay").getAsInt();
			if (jo.has("missingItemNotifications"))
				missingItemNotifications = jo.get("missingItemNotifications").getAsBoolean();
			if (jo.has("autoSwitch"))
				autoSwitch = jo.get("autoSwitch").getAsBoolean();
			if (jo.has("requireSeedHeld"))
				requireSeedHeld = jo.get("requireSeedHeld").getAsBoolean();
		} catch (Exception e) {
			LOGGER.error("Failed to load config", e);
		}
	}

	static void saveConfig() {
		JsonObject jo = new JsonObject();

		jo.add("enabled", new JsonPrimitive(enabled));
		jo.add("sneakToggle", new JsonPrimitive(sneakToggle));
		jo.add("useDelay", new JsonPrimitive(useDelay));
		jo.add("missingItemNotifications", new JsonPrimitive(missingItemNotifications));
		jo.add("autoSwitch", new JsonPrimitive(autoSwitch));
		jo.add("requireSeedHeld", new JsonPrimitive(requireSeedHeld));

		try {
			Files.createDirectories(configDir);
			Files.writeString(configFile, new Gson().toJson(jo));
		} catch (Exception e) {
			LOGGER.error("Failed to save config", e);
		}
	}
}