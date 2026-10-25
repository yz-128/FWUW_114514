package com.flansmod.client.gui;

import com.flansmod.common.BarrelHeat;
import com.flansmod.common.MechanicsSettings;
import com.flansmod.common.config.MechanicsConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The mechanics settings screen, opened from the mod list's Config button.
 *
 * <p>Three switches and the numbers the mechanics read. "Done" writes the config file and rebuilds
 * the runtime values; a data pack override still wins over the file, which is noted on screen.
 */
public final class MechanicsConfigScreen extends Screen
{
	private final Screen parent;

	private boolean enabled, barrelHeat, blockBreaking, legacyGlass;
	private double heatPerShotScale, heatCoolRateScale, healthPerHardness;
	private double plasticToughness, glassToughness, woodToughness, stoneToughness, metalToughness;
	private int maxSoftBlocks, maxBlocks;

	public MechanicsConfigScreen(Screen parent)
	{
		super(Component.translatable("config.flansmod.mechanics.title"));
		this.parent = parent;
	}

	@Override
	protected void init()
	{
		enabled = MechanicsConfig.ENABLED.get();
		barrelHeat = MechanicsConfig.BARREL_HEAT.get();
		blockBreaking = MechanicsConfig.BLOCK_BREAKING.get();
		legacyGlass = MechanicsConfig.LEGACY_GLASS.get();
		heatPerShotScale = MechanicsConfig.HEAT_PER_SHOT_SCALE.get();
		heatCoolRateScale = MechanicsConfig.HEAT_COOL_RATE_SCALE.get();
		healthPerHardness = MechanicsConfig.HEALTH_PER_HARDNESS.get();
		plasticToughness = MechanicsConfig.PLASTIC_TOUGHNESS.get();
		glassToughness = MechanicsConfig.GLASS_TOUGHNESS.get();
		woodToughness = MechanicsConfig.WOOD_TOUGHNESS.get();
		stoneToughness = MechanicsConfig.STONE_TOUGHNESS.get();
		metalToughness = MechanicsConfig.METAL_TOUGHNESS.get();
		maxSoftBlocks = MechanicsConfig.MAX_SOFT_BLOCKS.get();
		maxBlocks = MechanicsConfig.MAX_BLOCKS.get();

		int left = width / 2 - 150;
		int y = 36;
		addRenderableWidget(toggle(left, y, "config.flansmod.mechanics.enabled", enabled, value -> enabled = value));
		y += 24;
		addRenderableWidget(toggle(left, y, "config.flansmod.mechanics.barrel_heat", barrelHeat, value -> barrelHeat = value));
		y += 24;
		addRenderableWidget(toggle(left, y, "config.flansmod.mechanics.block_breaking", blockBreaking, value -> blockBreaking = value));
		y += 24;
		addRenderableWidget(toggle(left, y, "config.flansmod.mechanics.legacy_glass", legacyGlass, value -> legacyGlass = value));
		y += 24;
		addRenderableWidget(slider(left, y, "config.flansmod.mechanics.heat_per_shot", 0D, 5D, 0.05D,
				heatPerShotScale, value -> heatPerShotScale = value));
		y += 22;
		addRenderableWidget(slider(left, y, "config.flansmod.mechanics.heat_cool_rate", 0D, 5D, 0.05D,
				heatCoolRateScale, value -> heatCoolRateScale = value));
		y += 22;
		addRenderableWidget(slider(left, y, "config.flansmod.mechanics.health_per_hardness", 0.5D, 20D, 0.5D,
				healthPerHardness, value -> healthPerHardness = value));
		y += 22;
		addRenderableWidget(slider(left, y, "config.flansmod.mechanics.plastic_toughness", 0.05D, 20D, 0.05D,
				plasticToughness, value -> plasticToughness = value));
		y += 22;
		addRenderableWidget(slider(left, y, "config.flansmod.mechanics.glass_toughness", 0.05D, 10D, 0.05D,
				glassToughness, value -> glassToughness = value));
		y += 22;
		addRenderableWidget(slider(left, y, "config.flansmod.mechanics.wood_toughness", 0.05D, 20D, 0.05D,
				woodToughness, value -> woodToughness = value));
		y += 22;
		addRenderableWidget(slider(left, y, "config.flansmod.mechanics.stone_toughness", 0.05D, 20D, 0.05D,
				stoneToughness, value -> stoneToughness = value));
		y += 22;
		addRenderableWidget(slider(left, y, "config.flansmod.mechanics.metal_toughness", 0.05D, 20D, 0.05D,
				metalToughness, value -> metalToughness = value));
		y += 22;
		addRenderableWidget(sliderInt(left, y, "config.flansmod.mechanics.max_soft_blocks", 1, 64, maxSoftBlocks,
				value -> maxSoftBlocks = (int)value));
		y += 22;
		addRenderableWidget(sliderInt(left, y, "config.flansmod.mechanics.max_blocks", 1, 64, maxBlocks,
				value -> maxBlocks = (int)value));

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> save())
				.bounds(left, height - 32, 145, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
				.bounds(left + 155, height - 32, 145, 20).build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
	{
		renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
		graphics.drawCenteredString(font,
				Component.translatable("config.flansmod.mechanics.note"), width / 2, height - 48, 0xA0A0A0);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose()
	{
		if(minecraft != null)
			minecraft.setScreen(parent);
	}

	private void save()
	{
		MechanicsConfig.ENABLED.set(enabled);
		MechanicsConfig.BARREL_HEAT.set(barrelHeat);
		MechanicsConfig.BLOCK_BREAKING.set(blockBreaking);
		MechanicsConfig.LEGACY_GLASS.set(legacyGlass);
		MechanicsConfig.HEAT_PER_SHOT_SCALE.set(heatPerShotScale);
		MechanicsConfig.HEAT_COOL_RATE_SCALE.set(heatCoolRateScale);
		MechanicsConfig.HEALTH_PER_HARDNESS.set(healthPerHardness);
		MechanicsConfig.PLASTIC_TOUGHNESS.set(plasticToughness);
		MechanicsConfig.GLASS_TOUGHNESS.set(glassToughness);
		MechanicsConfig.WOOD_TOUGHNESS.set(woodToughness);
		MechanicsConfig.STONE_TOUGHNESS.set(stoneToughness);
		MechanicsConfig.METAL_TOUGHNESS.set(metalToughness);
		MechanicsConfig.MAX_SOFT_BLOCKS.set(maxSoftBlocks);
		MechanicsConfig.MAX_BLOCKS.set(maxBlocks);
		MechanicsConfig.SPEC.save();
		MechanicsConfig.reload();
		onClose();
	}

	private Button toggle(int x, int y, String key, boolean initial, java.util.function.Consumer<Boolean> setter)
	{
		var message = Component.translatable(initial ? "config.flansmod.mechanics.on" : "config.flansmod.mechanics.off");
		return Button.builder(Component.translatable(key).append(": ").append(message), button -> {
			boolean next = !button.getMessage().getString().endsWith(
					Component.translatable("config.flansmod.mechanics.on").getString());
			setter.accept(next);
			button.setMessage(Component.translatable(key).append(": ").append(
					Component.translatable(next ? "config.flansmod.mechanics.on" : "config.flansmod.mechanics.off")));
		}).bounds(x, y, 300, 20).build();
	}

	private AbstractSliderButton slider(int x, int y, String key, double min, double max, double step,
			double initial, java.util.function.DoubleConsumer setter)
	{
		return new AbstractSliderButton(x, y, 300, 20, Component.empty(), (initial - min) / (max - min))
		{
			{
				updateMessage();
			}

			@Override
			protected void updateMessage()
			{
				double shown = min + (max - min) * value;
				shown = min + Math.round((shown - min) / step) * step;
				setMessage(Component.translatable(key).append(": ")
						.append(String.format(java.util.Locale.ROOT, "%.2f", shown)));
			}

			@Override
			protected void applyValue()
			{
				double raw = min + (max - min) * value;
				raw = min + Math.round((raw - min) / step) * step;
				setter.accept(raw);
			}
		};
	}

	private AbstractSliderButton sliderInt(int x, int y, String key, int min, int max, int initial,
			java.util.function.IntConsumer setter)
	{
		return new AbstractSliderButton(x, y, 300, 20, Component.empty(), (double)(initial - min) / (max - min))
		{
			{
				updateMessage();
			}

			@Override
			protected void updateMessage()
			{
				int shown = (int)Math.round(min + (max - min) * value);
				setMessage(Component.translatable(key).append(": ").append(Integer.toString(shown)));
			}

			@Override
			protected void applyValue()
			{
				setter.accept((int)Math.round(min + (max - min) * value));
			}
		};
	}
}
