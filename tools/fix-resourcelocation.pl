#!/usr/bin/perl
# Rewrite `new ResourceLocation(...)` into the 1.21 static factories, using
# paren matching so nested expressions are handled correctly.
use strict;
use warnings;

my @files = @ARGV;
die "usage: fix-resourcelocation.pl <files...>\n" unless @files;

my $total_two = 0;
my $total_one = 0;
my $total_other = 0;

for my $file (@files) {
    open(my $in, '<:raw', $file) or die "open $file: $!";
    local $/;
    my $src = <$in>;
    close($in);

    my $out = '';
    my $pos = 0;
    my $changed = 0;

    while (1) {
        my $idx = index($src, 'new ResourceLocation(', $pos);
        last if $idx < 0;

        $out .= substr($src, $pos, $idx - $pos);
        my $start = $idx + length('new ResourceLocation(');
        my $depth = 1;
        my $i = $start;
        my @commas;
        my $in_str = 0;
        my $in_chr = 0;
        my $esc = 0;

        while ($i < length($src) && $depth > 0) {
            my $c = substr($src, $i, 1);
            if ($esc) { $esc = 0; }
            elsif ($c eq '\\') { $esc = 1; }
            elsif ($in_str) { if ($c eq '"') { $in_str = 0; } }
            elsif ($in_chr) { if ($c eq "'") { $in_chr = 0; } }
            elsif ($c eq '"') { $in_str = 1; }
            elsif ($c eq "'") { $in_chr = 1; }
            elsif ($c eq '(') { $depth++; }
            elsif ($c eq ')') { $depth--; }
            elsif ($c eq ',' && $depth == 1) { push @commas, $i; }
            $i++;
        }

        my $close = $i - 1;                 # index of the matching ')'
        my $inner = substr($src, $start, $close - $start);

        if (@commas == 1) {
            # two arguments -> fromNamespaceAndPath(ns, path)
            $out .= 'ResourceLocation.fromNamespaceAndPath(' . $inner . ')';
            $total_two++;
            $changed = 1;
        }
        elsif (@commas == 0 && $inner =~ /\S/) {
            # single argument -> parse(String)
            $out .= 'ResourceLocation.parse(' . $inner . ')';
            $total_one++;
            $changed = 1;
        }
        else {
            # anything else is left alone for manual review
            $out .= 'new ResourceLocation(' . $inner . ')';
            $total_other++;
            print STDERR "UNHANDLED ($file): new ResourceLocation($inner)\n";
            $pos = $close + 1;
            next;
        }
        $pos = $close + 1;
    }

    $out .= substr($src, $pos);

    if ($changed) {
        open(my $o, '>:raw', $file) or die "write $file: $!";
        print $o $out;
        close($o);
    }
}

print "two-arg -> fromNamespaceAndPath: $total_two\n";
print "one-arg -> parse:                 $total_one\n";
print "left for manual review:           $total_other\n";
