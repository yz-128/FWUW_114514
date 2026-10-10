#!/usr/bin/perl
# Replace the Forge-era JSON/byte-buffer recipe serializers with 1.21 codecs.
use strict;
use warnings;

my ($file, $editJsonStart) = @ARGV;
die "usage: fix-recipe-serializer.pl <file>\n" unless $file;

open(my $in, '<:raw', $file) or die "open: $!";
local $/;
my $src = <$in>;
close($in);

# The custom ingredients live behind Ingredient.getCustomIngredient() in 1.21,
# and the polymorphic Ingredient.CODEC handles both vanilla and custom entries.
$src =~ s/\bIngredient\.fromJson\(([^;]*?)\)/Ingredient.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, $1).getOrThrow()/gs;
$src =~ s/\bIngredient\.fromNetwork\((\w+)\)/Ingredient.CONTENTS_STREAM_CODEC.decode($1)/g;
$src =~ s/(\w+)\.toNetwork\((\w+)\)/Ingredient.CONTENTS_STREAM_CODEC.encode($2, $1)/g;

open(my $out, '>:raw', $file) or die "write: $!";
print $out $src;
close($out);
print "rewrote $file\n";
