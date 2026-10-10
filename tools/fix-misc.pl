#!/usr/bin/perl
# Remaining mechanical rewrites for the NeoForge 1.21 API.
#
#  FMLJavaModLoadingContext.get().getModEventBus() -> ModLoadingContext.get().getActiveContainer().getEventBus()
#  NetworkHooks.openScreen(player, provider, pos)  -> player.openMenu(provider, pos)
#  NetworkHooks.openScreen(player, provider)       -> player.openMenu(provider)
#  ITeleporter                                     -> (portal interface removed in 1.21)
#  NonNullSupplier                                 -> java.util.function.Supplier
use strict;
use warnings;

my @files = @ARGV;
die "usage: fix-misc.pl <files...>\n" unless @files;

my %counts;

for my $file (@files) {
    open(my $in, '<:raw', $file) or die "open $file: $!";
    local $/;
    my $src = <$in>;
    close($in);

    my $orig = $src;

    # --- FMLJavaModLoadingContext -> ModLoadingContext -------------------
    my $n = () = $src =~ /FMLJavaModLoadingContext\.get\(\)\.getModEventBus\(\)/g;
    if ($n) {
        $src =~ s/FMLJavaModLoadingContext\.get\(\)\.getModEventBus\(\)/ModLoadingContext.get().getActiveContainer().getEventBus()/g;
        $src =~ s/^import net\.neoforged\.fml\.javafmlmod\.FMLJavaModLoadingContext;\s*\n//gm;
        if ($src !~ /import net\.neoforged\.fml\.ModLoadingContext;/) {
            $src =~ s/^(package [^\n]*\n)/$1\nimport net.neoforged.fml.ModLoadingContext;\n/m;
        }
        $counts{'FMLJavaModLoadingContext.get().getModEventBus()'} += $n;
    }
    $n = () = $src =~ /FMLJavaModLoadingContext/g;
    if ($n) {
        $counts{'FMLJavaModLoadingContext (other)'} += $n;
    }

    # --- NetworkHooks.openScreen -> player.openMenu ----------------------
    $n = () = $src =~ /NetworkHooks\.openScreen\((\w+),\s*(getMenuProvider\([^()]*(?:\([^()]*\))?[^()]*\)|\w+(?:\.\w+)*),\s*(\w+\.\w+|\w+)\)/g;
    if ($n) {
        $src =~ s/NetworkHooks\.openScreen\((\w+),\s*(getMenuProvider\([^()]*(?:\([^()]*\))?[^()]*\)|\w+(?:\.\w+)*),\s*(\w+\.\w+|\w+)\)/$1.openMenu($2, $3)/g;
        $counts{'NetworkHooks.openScreen(3-arg)'} += $n;
    }
    $n = () = $src =~ /NetworkHooks\.openScreen\(\s*(\w+)\s*,\s*([^,()]+(?:\([^()]*\))?[^,()]*)\s*\)/g;
    if ($n) {
        $src =~ s/NetworkHooks\.openScreen\(\s*(\w+)\s*,\s*([^,()]+(?:\([^()]*\))?[^,()]*)\s*\)/$1.openMenu($2)/g;
        $counts{'NetworkHooks.openScreen(2-arg)'} += $n;
    }
    $n = () = $src =~ /NetworkHooks/g;
    if ($n) {
        $counts{'NetworkHooks (leftover)'} += $n;
    }

    # --- NonNullSupplier -> Supplier -------------------------------------
    $n = () = $src =~ /\bNonNullSupplier\b/g;
    if ($n) {
        $src =~ s/\bNonNullSupplier\b/Supplier/g;
        $src =~ s/^import net\.neoforged\.neoforge\.common\.util\.Supplier;\s*\n//gm;
        $src =~ s/^import net\.minecraftforge\.common\.util\.NonNullSupplier;\s*\n//gm;
        if ($src !~ /import java\.util\.function\.Supplier;/ && $src =~ /Supplier</) {
            $src =~ s/^(package [^\n]*\n)/$1\nimport java.util.function.Supplier;\n/m;
        }
        $counts{'NonNullSupplier -> Supplier'} += $n;
    }

    if ($src ne $orig) {
        open(my $o, '>:raw', $file) or die "write $file: $!";
        print $o $src;
        close($o);
    }
}

printf "%5d  %s\n", $counts{$_}, $_ for sort { $counts{$b} <=> $counts{$a} } keys %counts;
