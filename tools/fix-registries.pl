#!/usr/bin/perl
# Migrate Forge registry references and registration holder types to NeoForge 1.21.
#
#  ForgeRegistries.X          -> BuiltInRegistries.Y            (vanilla registries)
#  ForgeRegistries.Keys.Z     -> NeoForgeRegistries.Keys.Z      (NeoForge registries)
#  RegistryObject<X>          -> DeferredHolder<X, X>           (T extends R == X)
#  IForgeRegistry<T>          -> Registry<T>
use strict;
use warnings;

my @files = @ARGV;
die "usage: fix-registries.pl <files...>\n" unless @files;

my %vanilla = (
    'ForgeRegistries.ITEMS'              => 'BuiltInRegistries.ITEM',
    'ForgeRegistries.BLOCKS'             => 'BuiltInRegistries.BLOCK',
    'ForgeRegistries.ENTITY_TYPES'       => 'BuiltInRegistries.ENTITY_TYPE',
    'ForgeRegistries.BLOCK_ENTITY_TYPES' => 'BuiltInRegistries.BLOCK_ENTITY_TYPE',
    'ForgeRegistries.PARTICLE_TYPES'     => 'BuiltInRegistries.PARTICLE_TYPE',
    'ForgeRegistries.MENU_TYPES'         => 'BuiltInRegistries.MENU',
    'ForgeRegistries.RECIPE_TYPES'       => 'BuiltInRegistries.RECIPE_TYPE',
    'ForgeRegistries.RECIPE_SERIALIZERS' => 'BuiltInRegistries.RECIPE_SERIALIZER',
    'ForgeRegistries.MOB_EFFECTS'        => 'BuiltInRegistries.MOB_EFFECT',
    'ForgeRegistries.ENCHANTMENTS'       => 'BuiltInRegistries.ENCHANTMENT',
    'ForgeRegistries.ATTRIBUTES'         => 'BuiltInRegistries.ATTRIBUTE',
    'ForgeRegistries.FLUIDS'             => 'BuiltInRegistries.FLUID',
    'ForgeRegistries.SOUND_EVENTS'       => 'BuiltInRegistries.SOUND_EVENT',
    'ForgeRegistries.DAMAGE_TYPES'       => 'BuiltInRegistries.DAMAGE_TYPE',
);

my %neokeys = (
    'ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS' => 'NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS',
    'ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS'       => 'NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS',
    'ForgeRegistries.Keys.STRUCTURE_MODIFIER_SERIALIZERS'   => 'NeoForgeRegistries.Keys.STRUCTURE_MODIFIER_SERIALIZERS',
    'ForgeRegistries.Keys.FLUID_TYPES'                      => 'NeoForgeRegistries.Keys.FLUID_TYPES',
    'ForgeRegistries.Keys.INGREDIENT_TYPES'                 => 'NeoForgeRegistries.Keys.INGREDIENT_TYPES',
    'ForgeRegistries.Keys.RECIPE_SERIALIZERS'               => 'Registries.RECIPE_SERIALIZER',
    'ForgeRegistries.Keys.RECIPE_TYPES'                     => 'Registries.RECIPE_TYPE',
    'ForgeRegistries.Keys.ATTRIBUTES'                       => 'Registries.ATTRIBUTE',
    'ForgeRegistries.Keys.CREATIVE_MODE_TABS'               => 'Registries.CREATIVE_MODE_TAB',
);

my %counts;
my $holder_count = 0;

for my $file (@files) {
    open(my $in, '<:raw', $file) or die "open $file: $!";
    local $/;
    my $src = <$in>;
    close($in);

    my $orig = $src;

    for my $from (sort { length($b) <=> length($a) } keys %neokeys) {
        my $n = () = $src =~ /\Q$from\E/g;
        next unless $n;
        $src =~ s/\Q$from\E/$neokeys{$from}/g;
        $counts{$from} += $n;
    }
    for my $from (sort { length($b) <=> length($a) } keys %vanilla) {
        my $n = () = $src =~ /\Q$from\E/g;
        next unless $n;
        $src =~ s/\Q$from\E/$vanilla{$from}/g;
        $counts{$from} += $n;
    }

    # --- RegistryObject<X>  ->  DeferredHolder<X, X> ---------------------
    # Angle brackets nest, so walk them instead of using a single regex.
    while ($src =~ /\bRegistryObject</g) {
        my $start = $-[0];
        my $open  = $start + length('RegistryObject');
        my ($depth, $i) = (0, $open);
        while ($i < length($src)) {
            my $c = substr($src, $i, 1);
            if    ($c eq '<') { $depth++ }
            elsif ($c eq '>') { $depth--; last if $depth == 0 }
            $i++;
        }
        last if $i >= length($src);
        my $inner = substr($src, $open + 1, $i - $open - 1);
        my $replacement = "DeferredHolder<$inner, $inner>";
        $src = substr($src, 0, $start) . $replacement . substr($src, $i + 1);
        $holder_count++;
        pos($src) = $start + length($replacement);
    }

    if ($holder_count && $src =~ /\bDeferredHolder</) {
        # the old import pointed at DeferredHolder already (rewrite-imports.pl mapped
        # RegistryObject there), make sure exactly one import exists
        $src =~ s/^import net\.neoforged\.neoforge\.registries\.DeferredHolder;\s*\n//gm;
        if ($src !~ /import net\.neoforged\.neoforge\.registries\.DeferredHolder;/) {
            $src =~ s/^(package [^\n]*\n)/$1\nimport net.neoforged.neoforge.registries.DeferredHolder;\n/m;
        }
    }

    # BuiltInRegistries import when it is now referenced but not imported
    if ($src =~ /\bBuiltInRegistries\./ && $src !~ /import net\.minecraft\.core\.registries\.BuiltInRegistries;/) {
        $src =~ s/^(import net\.minecraft\.core\.registries\.Registries;)/import net.minecraft.core.registries.BuiltInRegistries;\n$1/m;
        if ($src !~ /import net\.minecraft\.core\.registries\.BuiltInRegistries;/) {
            $src =~ s/^(package [^\n]*\n)/$1\nimport net.minecraft.core.registries.BuiltInRegistries;\n/m;
        }
    }
    if ($src =~ /\bNeoForgeRegistries\./ && $src !~ /import net\.neoforged\.neoforge\.registries\.NeoForgeRegistries;/) {
        $src =~ s/^(package [^\n]*\n)/$1\nimport net.neoforged.neoforge.registries.NeoForgeRegistries;\n/m;
    }
    if ($src =~ /\bRegistries\.RECIPE_SERIALIZER|\bRegistries\.ATTRIBUTE|\bRegistries\.CREATIVE_MODE_TAB/ && $src !~ /import net\.minecraft\.core\.registries\.Registries;/) {
        $src =~ s/^(package [^\n]*\n)/$1\nimport net.minecraft.core.registries.Registries;\n/m;
    }

    if ($src ne $orig) {
        open(my $o, '>:raw', $file) or die "write $file: $!";
        print $o $src;
        close($o);
    }
}

printf "%5d  %s\n", $counts{$_}, $_ for sort { $counts{$b} <=> $counts{$a} } keys %counts;
print "\nRegistryObject usages rewritten: $holder_count\n";
