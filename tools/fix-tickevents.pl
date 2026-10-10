#!/usr/bin/perl
# Migrate Forge 1.20 tick handlers to the NeoForge 1.21 Pre/Post event model.
#
# The Phase enum is gone: each tick event now has a Pre and a Post subclass that
# is only fired in that phase, so a handler bound to one subclass can drop its
# phase check entirely.
use strict;
use warnings;

my @files = @ARGV;
die "usage: fix-tickevents.pl <files...>\n" unless @files;

my $changed_files = 0;
my $handlers = 0;
my %leftover;

for my $file (@files) {
    open(my $in, '<:raw', $file) or die "open $file: $!";
    local $/;
    my $src = <$in>;
    close($in);

    my $orig = $src;

    # --- 1. RenderTickEvent -> RenderFrameEvent.Pre ----------------------
    if ($src =~ /TickEvent\.RenderTickEvent/) {
        $src =~ s/TickEvent\.RenderTickEvent/RenderFrameEvent.Pre/g;
        $src =~ s/^import net\.neoforged\.neoforge\.event\.TickEvent;\s*\n//m;
        if ($src !~ /import net\.neoforged\.neoforge\.client\.event\.RenderFrameEvent;/) {
            $src =~ s/^(package [^\n]*\n)/$1\nimport net.neoforged.neoforge.client.event.RenderFrameEvent;\n/m;
        }
        $handlers++;
    }

    # --- 2. per-handler rewrite of client / server / level tick ----------
    # Walk each handler, inspect its body, choose Pre or Post, and drop the
    # now-meaningless phase comparison.
    my $scan = 0;
    while ($src =~ /(TickEvent\.(Client|Server|Level)TickEvent)(\s+\w+\s*\))/g) {
        my $kind = $2;
        my $match_start = $-[0];
        my $param_var_pos = $+[3];      # just after the ")" of the parameter list
        my $body_open = index($src, '{', $param_var_pos);
        last if $body_open < 0;

        # brace match to find the handler body
        my ($depth, $i, $in_str, $esc) = (0, $body_open, 0, 0);
        while ($i < length($src)) {
            my $c = substr($src, $i, 1);
            if ($esc) { $esc = 0 }
            elsif ($c eq '\\') { $esc = 1 }
            elsif ($in_str) { if ($c eq '"') { $in_str = 0 } }
            elsif ($c eq '"') { $in_str = 1 }
            elsif ($c eq '{') { $depth++ }
            elsif ($c eq '}') { $depth--; last if $depth == 0 }
            $i++;
        }
        my $body = substr($src, $body_open, $i - $body_open + 1);

        # variable name of the parameter
        my $sig = substr($src, $match_start, $body_open - $match_start);
        my ($var) = $sig =~ /TickEvent\s+(\w+)\s*$/;
        $var = 'event' unless defined $var;

        my $phase;
        if ($body =~ /\b\Q$var\E\.phase\s*==\s*TickEvent\.Phase\.START/) { $phase = 'Pre' }
        elsif ($body =~ /\b\Q$var\E\.phase\s*==\s*TickEvent\.Phase\.END/) { $phase = 'Post' }
        elsif ($body =~ /\b\Q$var\E\.phase\s*!=\s*TickEvent\.Phase\.START/) { $phase = 'Post' }
        elsif ($body =~ /\b\Q$var\E\.phase\s*!=\s*TickEvent\.Phase\.END/) { $phase = 'Pre' }
        else { $phase = 'Post' }   # no phase check: end-of-tick is the common intent

        my $new_type = "TickEvent.$kind$phase";

        # strip phase checks for this variable from the body
        my $nb = $body;
        $nb =~ s/^[ \t]*if\s*\(\s*\Q$var\E\.phase\s*==\s*TickEvent\.Phase\.(?:START|END)\s*\)\s*\r?\n//gm;
        $nb =~ s/^[ \t]*if\s*\(\s*\Q$var\E\.phase\s*!=\s*TickEvent\.Phase\.(?:START|END)\s*\)\s*(?:\r?\n\s*)?return\s*;\s*\r?\n//gm;
        # `if (x.phase == PHASE) { A }` with no else -> `{ A }` keeps working,
        # but with an else branch the whole chain must be reviewed by hand.
        if ($nb =~ /\Q$var\E\.phase/) {
            $leftover{$file} = 1;
        }

        my $new_sig = "TickEvent.$kind$phase" . substr($sig, length("TickEvent.$kind"));
        $src = substr($src, 0, $match_start) . $new_sig . $nb . substr($src, $i + 1);

        $handlers++;
        pos($src) = $match_start + length($new_sig);
        last if ++$scan > 200;
    }

    # --- 3. imports -------------------------------------------------------
    if ($src =~ /TickEvent\.ClientTickEvent/) {
        $src =~ s/^import net\.neoforged\.neoforge\.event\.TickEvent;\s*\n//m;
        if ($src !~ /import net\.neoforged\.neoforge\.client\.event\.ClientTickEvent;/) {
            $src =~ s/^(package [^\n]*\n)/$1\nimport net.neoforged.neoforge.client.event.ClientTickEvent;\n/m;
        }
    }
    if ($src =~ /TickEvent\.ServerTickEvent/) {
        $src =~ s/^import net\.neoforged\.neoforge\.event\.TickEvent;\s*\n//m;
        if ($src !~ /import net\.neoforged\.neoforge\.event\.tick\.ServerTickEvent;/) {
            $src =~ s/^(package [^\n]*\n)/$1\nimport net.neoforged.neoforge.event.tick.ServerTickEvent;\n/m;
        }
    }
    if ($src =~ /TickEvent\.LevelTickEvent/) {
        $src =~ s/^import net\.neoforged\.neoforge\.event\.TickEvent;\s*\n//m;
        if ($src !~ /import net\.neoforged\.neoforge\.event\.tick\.LevelTickEvent;/) {
            $src =~ s/^(package [^\n]*\n)/$1\nimport net.neoforged.neoforge.event.tick.LevelTickEvent;\n/m;
        }
    }

    if ($src ne $orig) {
        open(my $o, '>:raw', $file) or die "write $file: $!";
        print $o $src;
        close($o);
        $changed_files++;
    }
}

print "handlers rewritten: $handlers in $changed_files files\n";
if (keys %leftover) {
    print "NEEDS MANUAL REVIEW (phase logic not reducible):\n";
    print "  $_\n" for sort keys %leftover;
}
