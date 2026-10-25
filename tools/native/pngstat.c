/*
 * pngstat — offline PNG statistics / diff tool for the Flan's Mod verification pipeline.
 *
 * This is a **development tool only**. It is compiled by tools/native/build.ps1 and never enters
 * the mod jar: the Jar task packages src/main (see build.gradle sourceSets), and no source set
 * includes tools/. It exists because the GPU audit pipeline compares hundreds of 1304x648 frames
 * and answering "what is the grey block's exact colour and area" should not cost a JVM start.
 *
 * Usage:
 *   pngstat <image.png> [--region x y w h] [--diff other.png [--threshold N] [--out-diff f.png]]
 *                        [--top-colors N]
 *
 * Exit codes: 0 = success, 2 = usage error, 3 = I/O or decode failure.
 *
 * Image I/O is stb_image / stb_image_write (public domain / MIT), vendored under
 * tools/native/vendor. Nothing here depends on Minecraft, Java, or the game's assets.
 */
#define STB_IMAGE_IMPLEMENTATION
#include "vendor/stb_image.h"
#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "vendor/stb_image_write.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

typedef struct {
    int w, h;
    unsigned char *px; /* RGBA */
} Img;

static void die(int code, const char *msg) {
    fprintf(stderr, "pngstat: %s\n", msg);
    exit(code);
}

static unsigned char *load_png(const char *path, int *w, int *h) {
    int comp = 0;
    unsigned char *px = stbi_load(path, w, h, &comp, 4);
    if (!px)
        die(3, stbi_failure_reason());
    return px;
}

static inline double luma(const unsigned char *p) {
    return 0.2126 * p[0] + 0.7152 * p[1] + 0.0722 * p[2];
}

/* Prints mean/min/max/stddev per channel, luma stats, and the one-colour count. */
static void region_stats(const Img *img, int x0, int y0, int rw, int rh, int top_colors) {
    long n = 0;
    double sum[4] = {0, 0, 0, 0};
    unsigned char mn[4] = {255, 255, 255, 255}, mx[4] = {0, 0, 0, 0};
    double lsum = 0, lsum2 = 0;
    double lmin = 1e9, lmax = -1e9;
    unsigned int *packed = NULL;
    size_t cap = 0, used = 0;

    if (top_colors > 0) {
        cap = (size_t)rw * (size_t)rh;
        packed = (unsigned int *)malloc(cap * sizeof(unsigned int));
        if (!packed)
            die(3, "out of memory");
    }

    for (int y = y0; y < y0 + rh; ++y) {
        const unsigned char *row = img->px + ((size_t)y * img->w + x0) * 4;
        for (int x = 0; x < rw; ++x) {
            const unsigned char *p = row + x * 4;
            n++;
            for (int c = 0; c < 4; ++c) {
                sum[c] += p[c];
                if (p[c] < mn[c]) mn[c] = p[c];
                if (p[c] > mx[c]) mx[c] = p[c];
            }
            double l = luma(p);
            lsum += l;
            lsum2 += l * l;
            if (l < lmin) lmin = l;
            if (l > lmax) lmax = l;
            if (packed)
                packed[used++] = ((unsigned)p[0] << 16) | ((unsigned)p[1] << 8) | p[2];
        }
    }
    if (n == 0)
        die(2, "empty region");

    double lmean = lsum / n;
    double lvar = lsum2 / n - lmean * lmean;
    printf("region %dx%d at (%d,%d)  pixels=%ld\n", rw, rh, x0, y0, n);
    printf("  mean   R=%.2f G=%.2f B=%.2f A=%.2f\n", sum[0] / n, sum[1] / n, sum[2] / n, sum[3] / n);
    printf("  min    R=%d G=%d B=%d A=%d\n", mn[0], mn[1], mn[2], mn[3]);
    printf("  max    R=%d G=%d B=%d A=%d\n", mx[0], mx[1], mx[2], mx[3]);
    printf("  luma   mean=%.2f sd=%.2f min=%.2f max=%.2f\n", lmean,
           lvar > 0 ? sqrt(lvar) : 0.0, lmin, lmax);
    if (packed) {
        /* Count distinct colours by sorting the packed values; O(n log n) over one region. */
        for (size_t i = 1; i < used; ++i) {
            unsigned int key = packed[i];
            size_t j = i;
            while (j > 0 && packed[j - 1] > key) {
                packed[j] = packed[j - 1];
                --j;
            }
            packed[j] = key;
        }
        size_t distinct = 0;
        for (size_t i = 0; i < used; ++i)
            if (i == 0 || packed[i] != packed[i - 1]) ++distinct;
        printf("  colours distinct=%zu\n", distinct);

        size_t shown = 0;
        size_t run = 0;
        unsigned int cur = packed[0];
        for (size_t i = 1; i <= used; ++i) {
            if (i < used && packed[i] == cur) { ++run; continue; }
            ++run;
            printf("  top%d #%06x count=%zu (%.2f%%)\n", (int)shown + 1, cur, run, 100.0 * (double)run / (double)n);
            if (++shown >= (size_t)top_colors) break;
            if (i < used) { cur = packed[i]; run = 0; }
        }
        free(packed);
    }
}

static void diff_stats(const Img *a, const Img *b, int threshold, const char *out_diff) {
    if (a->w != b->w || a->h != b->h)
        die(2, "images differ in size; pass --region to compare a common area");
    long n = (long)a->w * a->h;
    long above = 0;
    double sum = 0;
    int maxd = 0;
    int bx0 = a->w, by0 = a->h, bx1 = -1, by1 = -1;
    unsigned char *heat = out_diff ? (unsigned char *)malloc((size_t)n * 4) : NULL;

    for (long i = 0; i < n; ++i) {
        const unsigned char *p = a->px + i * 4;
        const unsigned char *q = b->px + i * 4;
        int m = 0;
        for (int c = 0; c < 4; ++c) {
            int d = abs((int)p[c] - (int)q[c]);
            if (d > m) m = d;
        }
        sum += m;
        if (m > maxd) maxd = m;
        if (m > threshold) {
            ++above;
            int x = (int)(i % a->w), y = (int)(i / a->w);
            if (x < bx0) bx0 = x;
            if (y < by0) by0 = y;
            if (x > bx1) bx1 = x;
            if (y > by1) by1 = y;
        }
        if (heat) {
            unsigned char v = (unsigned char)(m > 255 ? 255 : m);
            heat[i * 4 + 0] = v;
            heat[i * 4 + 1] = 0;
            heat[i * 4 + 2] = (unsigned char)(255 - v);
            heat[i * 4 + 3] = 255;
        }
    }
    printf("diff %dx%d  pixels=%ld\n", a->w, a->h, n);
    printf("  changed(>%d)=%ld (%.3f%%)  mean delta=%.3f  max delta=%d\n",
           threshold, above, 100.0 * (double)above / (double)n, sum / (double)n, maxd);
    if (above > 0)
        printf("  bbox x=%d..%d y=%d..%d\n", bx0, bx1, by0, by1);
    if (heat) {
        if (!stbi_write_png(out_diff, a->w, a->h, 4, heat, a->w * 4))
            die(3, "cannot write diff image");
        printf("  wrote %s\n", out_diff);
        free(heat);
    }
}

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr,
                "usage: pngstat <image.png> [--region x y w h] [--diff other.png [--threshold N]"
                " [--out-diff f.png]] [--top-colors N]\n");
        return 2;
    }

    const char *path = argv[1];
    int x0 = -1, y0 = -1, rw = -1, rh = -1;
    int threshold = 0;
    int top_colors = 0;
    const char *diff_path = NULL;
    const char *out_diff = NULL;

    for (int i = 2; i < argc; ++i) {
        if (!strcmp(argv[i], "--region") && i + 4 < argc) {
            x0 = atoi(argv[++i]);
            y0 = atoi(argv[++i]);
            rw = atoi(argv[++i]);
            rh = atoi(argv[++i]);
        } else if (!strcmp(argv[i], "--diff") && i + 1 < argc) {
            diff_path = argv[++i];
        } else if (!strcmp(argv[i], "--threshold") && i + 1 < argc) {
            threshold = atoi(argv[++i]);
        } else if (!strcmp(argv[i], "--out-diff") && i + 1 < argc) {
            out_diff = argv[++i];
        } else if (!strcmp(argv[i], "--top-colors") && i + 1 < argc) {
            top_colors = atoi(argv[++i]);
        } else {
            fprintf(stderr, "pngstat: unknown argument %s\n", argv[i]);
            return 2;
        }
    }

    Img a = {0, 0, NULL};
    a.px = load_png(path, &a.w, &a.h);
    printf("image %s %dx%d\n", path, a.w, a.h);

    if (x0 < 0) { x0 = 0; y0 = 0; rw = a.w; rh = a.h; }
    if (x0 < 0 || y0 < 0 || rw <= 0 || rh <= 0 || x0 + rw > a.w || y0 + rh > a.h) {
        fprintf(stderr, "pngstat: region out of bounds (%d,%d %dx%d in %dx%d)\n",
                x0, y0, rw, rh, a.w, a.h);
        return 2;
    }

    Img b = {0, 0, NULL};
    if (diff_path)
        b.px = load_png(diff_path, &b.w, &b.h);

    if (diff_path) {
        diff_stats(&a, &b, threshold, out_diff);
    } else {
        region_stats(&a, x0, y0, rw, rh, top_colors);
    }

    stbi_image_free(a.px);
    if (b.px) stbi_image_free(b.px);
    return 0;
}
