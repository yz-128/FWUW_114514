#!/usr/bin/perl
# Bulk import rewrite: net.minecraftforge.* -> NeoForge 1.21.1 equivalents.
use strict;
use warnings;

my @files = @ARGV;
die "usage: rewrite-imports.pl <files...>\n" unless @files;

# Direct package/class moves that are pure renames.
my %map = (
    # FMLLoader / Mod lifecycle
    'net.minecraftforge.fml.common.Mod'                          => 'net.neoforged.fml.common.Mod',
    'net.minecraftforge.fml.ModLoadingContext'                   => 'net.neoforged.fml.ModLoadingContext',
    'net.minecraftforge.fml.ModContainer'                        => 'net.neoforged.fml.ModContainer',
    'net.minecraftforge.fml.config.ModConfig'                    => 'net.neoforged.fml.config.ModConfig',
    'net.minecraftforge.fml.loading.FMLEnvironment'              => 'net.neoforged.fml.loading.FMLEnvironment',
    'net.minecraftforge.fml.loading.FMLLoader'                   => 'net.neoforged.fml.loading.FMLLoader',
    'net.minecraftforge.fml.util.ObfuscationReflectionHelper'    => 'net.neoforged.fml.util.ObfuscationReflectionHelper',
    'net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent' => 'net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent',
    'net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent' => 'net.neoforged.fml.event.lifecycle.FMLClientSetupEvent',
    'net.minecraftforge.fml.event.IModBusEvent'                  => 'net.neoforged.fml.event.IModBusEvent',
    'net.minecraftforge.fml.InterModComms'                       => 'net.neoforged.fml.InterModComms',

    # Event bus
    'net.minecraftforge.eventbus.api.SubscribeEvent' => 'net.neoforged.bus.api.SubscribeEvent',
    'net.minecraftforge.eventbus.api.IEventBus'      => 'net.neoforged.bus.api.IEventBus',
    'net.minecraftforge.eventbus.api.EventPriority'  => 'net.neoforged.bus.api.EventPriority',
    'net.minecraftforge.eventbus.api.Event'          => 'net.neoforged.bus.api.Event',

    # Dist markers
    'net.minecraftforge.api.distmarker.Dist'   => 'net.neoforged.api.distmarker.Dist',
    'net.minecraftforge.api.distmarker.OnlyIn' => 'net.neoforged.api.distmarker.OnlyIn',

    # Common helpers
    'net.minecraftforge.common.MinecraftForge'                 => 'net.neoforged.neoforge.common.NeoForge',
    'net.minecraftforge.common.ForgeConfigSpec'               => 'net.neoforged.neoforge.common.ModConfigSpec',
    'net.minecraftforge.common.ForgeHooks'                     => 'net.neoforged.neoforge.common.CommonHooks',
    'net.minecraftforge.common.ForgeMod'                       => 'net.neoforged.neoforge.common.NeoForgeMod',
    'net.minecraftforge.common.Tags'                           => 'net.neoforged.neoforge.common.Tags',
    'net.minecraftforge.common.ToolAction'                     => 'net.neoforged.neoforge.common.ToolAction',
    'net.minecraftforge.common.ToolActions'                    => 'net.neoforged.neoforge.common.ToolActions',
    'net.minecraftforge.common.IExtensibleEnum'                => 'net.neoforged.neoforge.common.IExtensibleEnum',
    'net.minecraftforge.common.crafting.CraftingHelper'        => 'net.neoforged.neoforge.common.crafting.CraftingHelper',
    'net.minecraftforge.common.crafting.IIngredientSerializer' => 'net.neoforged.neoforge.common.crafting.ICustomIngredientSerializer',
    'net.minecraftforge.common.crafting.AbstractIngredient'    => 'net.neoforged.neoforge.common.crafting.ICustomIngredient',
    'net.minecraftforge.common.loot.IGlobalLootModifier'       => 'net.neoforged.neoforge.common.loot.IGlobalLootModifier',
    'net.minecraftforge.common.loot.LootModifier'             => 'net.neoforged.neoforge.common.loot.LootModifier',
    'net.minecraftforge.common.extensions.IForgeItem'          => 'net.neoforged.neoforge.common.extensions.IItemExtension',
    'net.minecraftforge.common.extensions.IForgeMenuType'      => 'net.neoforged.neoforge.common.extensions.IMenuTypeExtension',
    'net.minecraftforge.common.util.Lazy'                      => 'net.neoforged.neoforge.common.util.Lazy',
    'net.minecraftforge.common.util.INBTSerializable'          => 'net.neoforged.neoforge.common.util.INBTSerializable',

    # Registries
    'net.minecraftforge.registries.DeferredRegister' => 'net.neoforged.neoforge.registries.DeferredRegister',
    'net.minecraftforge.registries.RegistryObject'   => 'net.neoforged.neoforge.registries.DeferredHolder',
    'net.minecraftforge.registries.RegisterEvent'    => 'net.neoforged.neoforge.registries.RegisterEvent',
    'net.minecraftforge.registries.RegistryBuilder'  => 'net.neoforged.neoforge.registries.RegistryBuilder',
    'net.minecraftforge.registries.NewRegistryEvent' => 'net.neoforged.neoforge.registries.NewRegistryEvent',

    # Items / energy
    'net.minecraftforge.items.IItemHandler'            => 'net.neoforged.neoforge.items.IItemHandler',
    'net.minecraftforge.items.wrapper.SidedInvWrapper' => 'net.neoforged.neoforge.items.wrapper.SidedInvWrapper',
    'net.minecraftforge.energy.IEnergyStorage'         => 'net.neoforged.neoforge.energy.IEnergyStorage',
    'net.minecraftforge.energy.EnergyStorage'          => 'net.neoforged.neoforge.energy.EnergyStorage',

    # Server
    'net.minecraftforge.server.ServerLifecycleHooks' => 'net.neoforged.neoforge.server.ServerLifecycleHooks',

    # Client rendering / model
    'net.minecraftforge.client.event.'                          => 'net.neoforged.neoforge.client.event.',
    'net.minecraftforge.client.extensions.common.IClientItemExtensions' => 'net.neoforged.neoforge.client.extensions.common.IClientItemExtensions',
    'net.minecraftforge.client.model.BakedModelWrapper'          => 'net.neoforged.neoforge.client.model.BakedModelWrapper',
    'net.minecraftforge.client.model.data.ModelData'             => 'net.neoforged.neoforge.client.model.data.ModelData',
    'net.minecraftforge.client.model.geometry.IUnbakedGeometry'  => 'net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry',
    'net.minecraftforge.client.model.geometry.IGeometryBakingContext' => 'net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext',
    'net.minecraftforge.client.model.geometry.IGeometryLoader'   => 'net.neoforged.neoforge.client.model.geometry.IGeometryLoader',
    'net.minecraftforge.client.settings.KeyConflictContext'      => 'net.neoforged.neoforge.client.settings.KeyConflictContext',
    'net.minecraftforge.client.settings.IKeyConflictContext'     => 'net.neoforged.neoforge.client.settings.IKeyConflictContext',
    'net.minecraftforge.client.textures.UnitTextureAtlasSprite'  => 'net.neoforged.neoforge.client.textures.UnitTextureAtlasSprite',

    # Events (common)
    'net.minecraftforge.event.' => 'net.neoforged.neoforge.event.',

    # Network
    'net.minecraftforge.network.PacketDistributor' => 'net.neoforged.neoforge.network.PacketDistributor',
);

my %counts;
for my $file (@files) {
    open(my $in, '<:raw', $file) or die "open $file: $!";
    local $/;
    my $src = <$in>;
    close($in);

    my $orig = $src;

    # Longest keys first so specific entries beat the generic package prefixes.
    for my $from (sort { length($b) <=> length($a) } keys %map) {
        my $to = $map{$from};
        my $n = () = $src =~ /\Q$from\E/g;
        next unless $n;
        $src =~ s/\Q$from\E/$to/g;
        $counts{$from} += $n;
    }

    if ($src ne $orig) {
        open(my $o, '>:raw', $file) or die "write $file: $!";
        print $o $src;
        close($o);
    }
}

for my $k (sort { $counts{$b} <=> $counts{$a} } keys %counts) {
    printf "%5d  %s\n", $counts{$k}, $k;
}
print "\nTotal rewrites: ", (eval join('+', values %counts) // 0), "\n";
