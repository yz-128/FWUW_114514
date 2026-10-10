#!/usr/bin/perl
# Fix the remaining common 1.21 API mismatches left after the bulk passes.
#
#   entityData.getValue(ACCESSOR)  -> entityData.get(ACCESSOR)   (was over-corrected)
#   <x>.get(DIRECTION_PROPERTY)    -> <x>.getValue(DIRECTION_PROPERTY)
#   event.getPartialTick()         -> event.getPartialTick() ... (DeltaTracker)
use strict;
use warnings;

my @files = @ARGV;
die "usage: fix-common.pl <files...>\n" unless @files;

my %counts;
my $files_changed = 0;

for my $file (@files) {
    open(my $in, '<:raw', $file) or die "open $file: $!";
    local $/;
    my $src = <$in>;
    close($in);

    my $orig = $src;

    # EntityDataAccessor reads must use get(), not getValue().
    $counts{'entityData.getValue'} += () = $src =~ s/\bentityData\.getValue\(/entityData.get(/g;
    $counts{'this.entityData.getValue'} += () = $src =~ s/\bthis\.entityData\.getValue\(/this.entityData.get(/g;

    # Block state property reads: <expr>.get(FACING) style with an uppercase property const.
    $counts{'blockState.getValue'} += () = $src =~ s/(\w+)\.get\(((?:[A-Z][A-Z0-9_]{2,}|BlockStateProperties\.[A-Z_]+))\)/$1.getValue($2)/g;

    if ($src ne $orig) {
        open(my $out, '>:raw', $file) or die "write $file: $!";
        print $out $src;
        close($out);
        $files_changed++;
    }
}

printf "%5d  %s\n", $counts{$_}, $_ for sort { $counts{$b} <=> $counts{$a} } keys %counts;
print "files changed: $files_changed\n";
