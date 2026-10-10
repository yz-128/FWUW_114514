#!/usr/bin/perl
# Inside defineSynchedData, entityData.define(...) must become builder.define(...)
# (NeoForge 1.21 passes the SynchedEntityData.Builder as a parameter).
use strict;
use warnings;

my @files = @ARGV;
die "usage: fix-entity-data.pl <files...>\n" unless @files;

my $files_changed = 0;
my $rewrites = 0;

for my $file (@files) {
    open(my $in, '<:raw', $file) or die "open $file: $!";
    local $/;
    my $src = <$in>;
    close($in);

    my $orig = $src;
    my @lines = split /\n/, $src, -1;
    my $inside = 0;
    my $depth = 0;

    for my $i (0 .. $#lines) {
        if ($lines[$i] =~ /defineSynchedData\(SynchedEntityData\.Builder/) {
            $inside = 1;
            $depth = 0;
        }
        if ($inside) {
            my $opens  = () = $lines[$i] =~ /\{/g;
            my $closes = () = $lines[$i] =~ /\}/g;
            my $count  = () = $lines[$i] =~ s/entityData\.define\(/builder.define(/g;
            $rewrites += $count;
            $depth += $opens - $closes;
            if ($depth <= 0 && $lines[$i] =~ /\}/) {
                $inside = 0;
            }
        }
    }
    $src = join "\n", @lines;

    if ($src ne $orig) {
        if ($src !~ /import net\.minecraft\.network\.syncher\.SynchedEntityData;/) {
            $src =~ s/^(package [^\n]*\n)/$1\nimport net.minecraft.network.syncher.SynchedEntityData;\n/m;
        }
        open(my $out, '>:raw', $file) or die "write $file: $!";
        print $out $src;
        close($out);
        $files_changed++;
    }
}

print "files changed: $files_changed, rewrites: $rewrites\n";
