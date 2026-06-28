package xyz.ryhon.replanterplus;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

public class ConfigScreen extends Screen {
	Screen parent;

	SwitchButton enabledButton;
	SwitchButton sneakToggleButton;
	SwitchButton missingItemNotificationsButton;
	SwitchButton autoSwitchButton;
	SwitchButton requireSeedHeldButton;
	SimpleSlider tickDelaySlider;
	Button doneButton;

	public ConfigScreen(Screen parent) {
		super(Component.empty());
		this.parent = parent;
	}

	@Override
	protected void init() {
		super.init();

		int buttonWidth = 48;
		int buttonHeight = 18;
		int panelWidth = 256;

		enabledButton = new SwitchButton(
				(width / 2) + (panelWidth / 2) - (buttonWidth), (height / 2) - (buttonHeight * 4),
				buttonWidth, buttonHeight, ReplanterPlus.enabled) {
			@Override
			public void setToggled(boolean toggled) {
				super.setToggled(toggled);
				ReplanterPlus.enabled = toggled;
			}
		};
		addRenderableWidget(enabledButton);
		addWidget(enabledButton);
		StringWidget t = new StringWidget(Component.translatable("replanter.configscreen.enabled"), font);
		t.setPosition((width / 2) - (panelWidth / 2),
				enabledButton.getY() + (buttonHeight / 2) - (font.lineHeight / 2));
		addRenderableWidget(t);

		sneakToggleButton = new SwitchButton(
				enabledButton.getX(), enabledButton.getY() + enabledButton.getHeight(),
				enabledButton.getWidth(), enabledButton.getHeight(),
				ReplanterPlus.sneakToggle) {
			@Override
			public void setToggled(boolean toggled) {
				super.setToggled(toggled);
				ReplanterPlus.sneakToggle = toggled;
			}
		};
		addRenderableWidget(sneakToggleButton);
		addWidget(sneakToggleButton);
		t = new StringWidget(Component.translatable("replanter.configscreen.sneakToggle"), font);
		t.setPosition((width / 2) - (panelWidth / 2),
				sneakToggleButton.getY() + (buttonHeight / 2) - (font.lineHeight / 2));
		addRenderableWidget(t);

		missingItemNotificationsButton = new SwitchButton(
				sneakToggleButton.getX(), sneakToggleButton.getY() + sneakToggleButton.getHeight(),
				sneakToggleButton.getWidth(), sneakToggleButton.getHeight(),
				ReplanterPlus.missingItemNotifications) {
			@Override
			public void setToggled(boolean toggled) {
				super.setToggled(toggled);
				ReplanterPlus.missingItemNotifications = toggled;
			}
		};
		addRenderableWidget(missingItemNotificationsButton);
		addWidget(missingItemNotificationsButton);
		t = new StringWidget(Component.translatable("replanter.configscreen.missingItemNotifications"), font);
		t.setPosition((width / 2) - (panelWidth / 2),
				missingItemNotificationsButton.getY() + (buttonHeight / 2) - (font.lineHeight / 2));
		addRenderableWidget(t);

		autoSwitchButton = new SwitchButton(
				missingItemNotificationsButton.getX(),
				missingItemNotificationsButton.getY() + missingItemNotificationsButton.getHeight(),
				missingItemNotificationsButton.getWidth(), missingItemNotificationsButton.getHeight(),
				ReplanterPlus.autoSwitch) {
			@Override
			public void setToggled(boolean toggled) {
				super.setToggled(toggled);
				ReplanterPlus.autoSwitch = toggled;
			}
		};
		addRenderableWidget(autoSwitchButton);
		addWidget(autoSwitchButton);
		t = new StringWidget(Component.translatable("replanter.configscreen.autoSwitch"), font);
		t.setPosition((width / 2) - (panelWidth / 2),
				autoSwitchButton.getY() + (buttonHeight / 2) - (font.lineHeight / 2));
		addRenderableWidget(t);

		requireSeedHeldButton = new SwitchButton(
				autoSwitchButton.getX(),
				autoSwitchButton.getY() + autoSwitchButton.getHeight(),
				autoSwitchButton.getWidth(), autoSwitchButton.getHeight(),
				ReplanterPlus.requireSeedHeld) {
			@Override
			public void setToggled(boolean toggled) {
				super.setToggled(toggled);
				ReplanterPlus.requireSeedHeld = toggled;
			}
		};
		addRenderableWidget(requireSeedHeldButton);
		addWidget(requireSeedHeldButton);
		t = new StringWidget(Component.translatable("replanter.configscreen.requireSeedHeld"), font);
		t.setPosition((width / 2) - (panelWidth / 2),
				requireSeedHeldButton.getY() + (buttonHeight / 2) - (font.lineHeight / 2));
		addRenderableWidget(t);

		t = new StringWidget(Component.translatable("replanter.configscreen.tickDelay"), font);
		t.setPosition((width / 2) - (panelWidth / 2),
				requireSeedHeldButton.getY() + buttonHeight);
		addRenderableWidget(t);

		tickDelaySlider = new SimpleSlider(0, 8);
		tickDelaySlider.setPosition(t.getX(), t.getY() + t.getHeight());
		tickDelaySlider.setWidth(panelWidth);
		tickDelaySlider.setHeight(24);
		tickDelaySlider.setIValue(ReplanterPlus.useDelay);
		tickDelaySlider.onValue = (Long l) -> {
			long i = l;
			ReplanterPlus.useDelay = (int) i;
		};
		addRenderableWidget(tickDelaySlider);
		addWidget(tickDelaySlider);

		doneButton = Button.builder(Component.translatable("replanter.configscreen.done"), (Button b) -> {
			onClose();
		})
				.size(96, 24)
				.pos((width / 2) - (96 / 2), tickDelaySlider.getY() + tickDelaySlider.getHeight() + 8)
				.build();
		addRenderableWidget(doneButton);
		addWidget(doneButton);
	}

	public static class SimpleSlider extends AbstractSliderButton {
		long min, max;
		long iValue;
		public Consumer<Long> onValue;

		public SimpleSlider(long min, long max) {
			super(0, 0, 0, 0, Component.empty(), 0);
			this.min = min;
			this.max = max;
			updateMessage();
		}

		public void setIValue(long v) {
			iValue = v;
			setValue((v - min) / (double) (max - min));
		}

		@Override
		protected void applyValue() {
			iValue = (long) Math.round(value * (max - min)) + min;
			setIValue(iValue);

			updateMessage();
			if (onValue != null)
				onValue.accept(iValue);
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(iValue + " / " + max));
		}
	}

	public class SwitchButton extends AbstractButton {
		public boolean toggled = false;

		public SwitchButton(int i, int j, int k, int l, boolean _toggled) {
			super(i, j, k, l, Component.empty());
			toggled = _toggled;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
			extractDefaultSprite(context);
			context.centeredText(font,
					net.minecraft.network.chat.Component.translatable("replanter.switchbutton.label." + (toggled ? "on" : "off")),
					getX() + (width / 2), getY() + (height / 2) - (font.lineHeight / 2),
					toggled ? 0xff00ff00 : 0xffff0000);
		}

		@Override
		public void onPress(InputWithModifiers input) {
			setToggled(!toggled);
		}

		public void setToggled(boolean toggled) {
			this.toggled = toggled;
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput builder) {
		}
	}

	@Override
	public void onClose() {
		minecraft.getInstance().setScreenAndShow(parent);
		ReplanterPlus.saveConfig();
	}
}
