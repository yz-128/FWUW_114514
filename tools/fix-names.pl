#!/usr/bin/perl
# Rewrite unqualified Forge type names left behind by the import-level pass.
use strict;
use warnings;

my @files = @ARGV;
die "usage: fix-names.pl <files...>\n" unless @files;

# Simple identifier renames, safe because the old names do not appear in MC itself.
my %idents = (
    'ForgeConfigSpec'   => 'ModConfigSpec',
    'IForgeItem'        => 'IItemExtension',
    'IForgeMenuType'    => 'IMenuTypeExtension',
    'ForgeHooks'        => 'CommonHooks',
    'ForgeMod'          => 'NeoForgeMod',
    'ForgeCapabilities' => 'Capabilities',
);

# Fully-qualified replacements done as a second stage by the caller.
my %counts;

for my $file (@files) {
    open(my $in, '<:raw', $file) or die "open $file: $!";
    local $/;
    my $src = <$in>;
    close($in);

    my $orig = $src;
    for my $from (keys %idents) {
        my $n = () = $src =~ /\b\Q$from\E\b/g;
        next unless $n;
        $src =~ s/\b\Q$from\E\b/$idents{$from}/g;
        $counts{$from} += $n;
    }

    if ($src ne $orig) {
        open(my $o, '>:raw', $file) or die "write $file: $!";
        print $o $src;
        close($o);
    }
}

printf "%5d  %s\n", $counts{$_}, $_ for sort { $counts{$b} <=> $counts{$a} } keys %counts;
print "\nTotal: ", (eval join('+', values %counts) // 0), "\n";
