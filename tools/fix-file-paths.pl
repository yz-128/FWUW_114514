#!/usr/bin/perl
# Convert the remaining java.io.File usages to java.nio.file.Path.
# In 1.21 MinecraftServer#getServerDirectory and friends return Path, so the
# callers that still build Files from a path string must follow.
use strict;
use warnings;

my @files = @ARGV;
die "usage: fix-file-paths.pl <files...>\n" unless @files;

my %counts;
for my $file (@files) {
    open(my $in, '<:raw', $file) or die "open $file: $!";
    local $/;
    my $src = <$in>;
    close($in);
    my $orig = $src;

    # new File(<expr>.getPath() + "...") -> <expr>.resolve("...")
    $src =~ s/new\s+File\(\s*(\w+)\.getPath\(\)\s*\+\s*"([^"]*)"\s*\)/($1.resolve("$2"))/g;
    # new File(x + "/...")  (string concat already)
    $src =~ s/new\s+File\(\s*(\w+)\s*\+\s*"([^"]*)"\s*\)/($1.resolve("$2"))/g;
    # new File(x.getPath()) -> x
    $src =~ s/new\s+File\(\s*(\w+)\.getPath\(\)\s*\)/$1/g;
    # exists()/isDirectory()/getName() -> NIO equivalents
    $src =~ s/\b(\w+(?:\.\w+)*)\.exists\(\)/java.nio.file.Files.exists($1)/g;
    $src =~ s/\b(\w+(?:\.\w+)*)\.isDirectory\(\)/java.nio.file.Files.isDirectory($1)/g;
    $src =~ s/\b(\w+(?:\.\w+)*)\.getName\(\)/$1.getFileName().toString()/g;
    $src =~ s/\b(\w+(?:\.\w+)*)\.listFiles\(\)/java.nio.file.Files.list($1).toList()/g;
    $src =~ s/\b(\w+(?:\.\w+)*)\.mkdirs\(\)/java.nio.file.Files.createDirectories($1)/g;
    $src =~ s/\b(\w+(?:\.\w+)*)\.toFile\(\)/$1/g;

    if ($src ne $orig) {
        open(my $out, '>:raw', $file) or die "write $file: $!";
        print $out $src;
        close($out);
        $counts{$file} = 1;
    }
}

print "files changed: ", scalar(keys %counts), "\n";
