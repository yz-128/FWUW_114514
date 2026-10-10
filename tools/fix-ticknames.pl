#!/usr/bin/perl
# Repair the tick handler type names emitted by fix-tickevents.pl.
# The earlier pass produced "TickEvent.ClientPreTickEvent" etc; the real
# NeoForge types are the nested Pre/Post classes of the specific event.
use strict;
use warnings;

my @files = @ARGV;
die "usage: fix-ticknames.pl <files...>\n" unless @files;

my %map = (
    'TickEvent.ClientPreTickEvent'  => 'ClientTickEvent.Pre',
    'TickEvent.ClientPostTickEvent' => 'ClientTickEvent.Post',
    'TickEvent.ServerPreTickEvent'  => 'ServerTickEvent.Pre',
    'TickEvent.ServerPostTickEvent' => 'ServerTickEvent.Post',
    'TickEvent.LevelPreTickEvent'   => 'LevelTickEvent.Pre',
    'TickEvent.LevelPostTickEvent'  => 'LevelTickEvent.Post',
);

my %counts;
for my $file (@files) {
    open(my $in, '<:raw', $file) or die "open $file: $!";
    local $/;
    my $src = <$in>;
    close($in);

    my $orig = $src;
    for my $from (keys %map) {
        my $n = () = $src =~ /\Q$from\E/g;
        next unless $n;
        $src =~ s/\Q$from\E/$map{$from}/g;
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
