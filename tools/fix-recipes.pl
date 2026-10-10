#!/usr/bin/perl
# Migrate the 1.20 recipe API to 1.21:
#   getResultItem(RegistryAccess)  -> getResultItem(HolderLookup.Provider)
#   assemble(T, RegistryAccess)    -> assemble(T, HolderLookup.Provider)
#   drop the getId() override       (recipes no longer carry an id)
#   `ingredient instanceof X`       -> `ingredient.getCustomIngredient() instanceof X`
#   Recipe<T> requires T extends RecipeInput
use strict;
use warnings;

my @files = @ARGV;
die "usage: fix-recipes.pl <files...>\n" unless @files;

my %counts;
for my $file (@files) {
    open(my $in, '<:raw', $file) or die "open $file: $!";
    local $/;
    my $src = <$in>;
    close($in);

    my $orig = $src;

    $counts{'getResultItem'} += ($src =~ s/getResultItem\(@Nonnull RegistryAccess registryAccess\)/getResultItem(@Nonnull HolderLookup.Provider registryAccess)/g);
    $counts{'getResultItem'} += ($src =~ s/getResultItem\(RegistryAccess registryAccess\)/getResultItem(HolderLookup.Provider registryAccess)/g);
    $counts{'assemble'} += ($src =~ s/assemble\((\@Nonnull [\w.]+ \w+), \@Nonnull RegistryAccess (\w+)\)/assemble($1, \@Nonnull HolderLookup.Provider $2)/g);

    # recipes no longer expose getId(); keep the method but stop overriding
    $counts{'getId'} += ($src =~ s/\@Nonnull\n\t(\@Override\n)\tpublic ResourceLocation getId\(\)/\t$1public ResourceLocation getId()/g);

    # custom ingredient tests
    $counts{'customIngredient'} += ($src =~ s/ingredient instanceof (StackedIngredient|TieredMaterialIngredient|TieredPartIngredient|IExtraIngredientTooltip)\b/ingredient.getCustomIngredient() instanceof $1/g);
    $counts{'customIngredientVar'} += ($src =~ s/input instanceof (TieredPartIngredient|TieredMaterialIngredient)\b/input.getCustomIngredient() instanceof $1/g);

    if ($src =~ /\bHolderLookup\b/ && $src !~ /import net\.minecraft\.core\.HolderLookup;/) {
        $src =~ s/^(package [^\n]*\n)/$1\nimport net.minecraft.core.HolderLookup;\n/m;
    }

    if ($src ne $orig) {
        open(my $o, '>:raw', $file) or die "write $file: $!";
        print $o $src;
        close($o);
    }
}

printf "%5d  %s\n", $counts{$_}, $_ for sort { $counts{$b} <=> $counts{$a} } keys %counts;
