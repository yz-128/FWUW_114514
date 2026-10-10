#!/usr/bin/perl
# Validate zh_cn.json files using Minecraft's OWN format-specifier regex.
#
# Minecraft (TranslatableContents.FORMAT_PATTERN) treats "%<N$><[A-Za-z%]|end>" as a
# format specifier.  A literal '%' that does NOT match is safe, but one at the very END
# of a string DOES match (the '|$' alternative) and will be looked up as an argument.
#
# Therefore: the sequence of format specifiers (as MC sees them) must be identical
# between en_us and zh_cn.  This script reports any divergence.
use strict; use warnings; use utf8; use JSON::PP;
binmode(STDOUT, ':encoding(UTF-8)');

my $root = 'E:/JAVAMCMODGJ/FLAN 移植i/flanmod-template-1.21.1/src/main/resources/assets';
my @mods = qw(flanmod flansbasicparts flansclockworkcalamity flansmod
              flansphysicsmod flansteams flansvendersgame flansworldwars);

my $MC_FORMAT = qr/%((\d+)\$)?([A-Za-z%]|$)/;

my ($errs, $totalKeys) = (0, 0);

for my $mod (@mods) {
    my ($en_f, $zh_f) = ("$root/$mod/lang/en_us.json", "$root/$mod/lang/zh_cn.json");
    next unless -f $en_f;
    unless (-f $zh_f) { print "MISSING  $mod/lang/zh_cn.json\n"; $errs++; next; }

    my ($en, $zh);
    for my $pair ([$en_f, \$en], [$zh_f, \$zh]) {
        my ($path, $ref) = @$pair;
        open my $fh, '<:raw', $path or die "open $path: $!";
        local $/; my $txt = <$fh>; close $fh;
        $txt =~ s/^\x{EF}\x{BB}\x{BF}//;
        eval { $$ref = decode_json($txt) };
        if ($@) { chomp(my $e = $@); print "BAD-JSON $path : $e\n"; $errs++; next; }
    }
    next unless $en && $zh;

    my @missing = grep { !exists $zh->{$_} } keys %$en;
    $totalKeys += scalar keys %$en;

    my $bad = 0;
    for my $k (sort keys %$en) {
        next unless exists $zh->{$k};
        my @a = spec($en->{$k});
        my @b = spec($zh->{$k});
        if ("@a" ne "@b") {
            $bad++;
            if ($bad <= 10) {
                printf "  [%s] %s\n", $mod, $k;
                printf "      en spec: [%s]  %s\n", join(',', @a), $en->{$k};
                printf "      zh spec: [%s]  %s\n", join(',', @b), $zh->{$k};
            }
        }
    }
    printf "%-22s en=%-4d zh=%-4d missing=%-3d spec-bad=%d\n",
           $mod, scalar(keys %$en), scalar(keys %$zh), scalar(@missing), $bad;
    if (@missing) { $errs++; print "   missing: @missing\n" }
    if ($bad)     { $errs++; }
}

print "\nkeys: $totalKeys\nerrors: $errs\n";
print($errs ? "RESULT: FAIL\n" : "RESULT: PASS\n");

sub spec {
    my ($s) = @_;
    return () unless defined $s;
    # '%%' is Minecraft's escaped literal percent: it consumes NO argument, so it must
    # be removed before scanning, otherwise it looks like a bogus specifier.
    $s =~ s/%%//g;
    my @out;
    while ($s =~ /$MC_FORMAT/g) {
        my ($idx, $conv) = ($2, $3);
        # A bare '%' at end-of-string matches MC's '|$' alternative, which is a malformed
        # specifier: MC throws TranslatableFormatException and catches it, falling back to
        # the raw text.  Semantically that '%' is a LITERAL, so it is not an argument slot
        # and must not be compared as one.
        next if !defined $conv || !length $conv;
        push @out, (defined $idx && length $idx) ? "%$idx\$$conv" : "%$conv";
    }
    return @out;
}
