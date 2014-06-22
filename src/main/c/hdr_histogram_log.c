/**
 * hdr_histogram.c
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

#include <stdlib.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <zlib.h>
#include <errno.h>
#include <sys/stat.h>

#include "hdr_histogram.h"
#include "hdr_histogram_log.h"

#ifdef __APPLE__

#include <libkern/OSByteOrder.h>

#define htobe16(x) OSSwapHostToBigInt16(x)
#define htole16(x) OSSwapHostToLittleInt16(x)
#define be16toh(x) OSSwapBigToHostInt16(x)
#define le16toh(x) OSSwapLittleToHostInt16(x)

#define htobe32(x) OSSwapHostToBigInt32(x)
#define htole32(x) OSSwapHostToLittleInt32(x)
#define be32toh(x) OSSwapBigToHostInt32(x)
#define le32toh(x) OSSwapLittleToHostInt32(x)

#define htobe64(x) OSSwapHostToBigInt64(x)
#define htole64(x) OSSwapHostToLittleInt64(x)
#define be64toh(x) OSSwapBigToHostInt64(x)
#define le64toh(x) OSSwapLittleToHostInt64(x)

#elif __linux__

#include <endian.h>

#else

#warning "Platform not supported\n"

#endif

static const char base64_table[] =
{
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
    'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
    'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
    'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/', '\0'
};

static int get_base_64(uint32_t _24_bit_value, int shift)
{
    return (0x3F & (_24_bit_value >> shift));
}

static int from_base_64(int c)
{
    if ('A' <= c && c <= 'Z')
    {
        return c - 'A';
    }
    else if ('a' <= c && c <= 'z')
    {
        return c - ('a' + 26);
    }
    else if ('0' <= c && c <= '9')
    {
        return c - ('0' + 52);
    }
    else if ('+' == c)
    {
        return 62;
    }
    else if ('/' == c)
    {
        return 63;
    }

    return -1;
}

int base64_encode(uint8_t* buf, int length, FILE* f)
{
    int i = 0;
    uint32_t in_val = 0;

    for (; length - i >= 3; i += 3)
    {
        in_val = (buf[i] << 16) + (buf[i + 1] << 8) + (buf[i + 2]);

        putc(base64_table[get_base_64(in_val, 18)], f);
        putc(base64_table[get_base_64(in_val, 12)], f);
        putc(base64_table[get_base_64(in_val,  6)], f);
        putc(base64_table[get_base_64(in_val,  0)], f);
    }

    int remaining = length - i;

    switch (remaining)
    {
        case 2:
            in_val = (buf[i] << 16) + (buf[i + 1] << 8);
            putc(base64_table[get_base_64(in_val, 18)], f);
            putc(base64_table[get_base_64(in_val, 12)], f);
            putc(base64_table[get_base_64(in_val,  6)], f);
            putc('=', f);
            break;

        case 1:
            in_val = (buf[i] << 16);
            putc(base64_table[get_base_64(in_val, 18)], f);
            putc(base64_table[get_base_64(in_val, 12)], f);
            putc('=', f);
            putc('=', f);
            break;
    }

    return 0;
}

#define _READ_BUF_SIZE 4

int base64_decode(uint8_t* buf, int length, char term, FILE* f)
{
    char read_buf[_READ_BUF_SIZE];
    int ibuf = 0;
    do
    {
        printf("read: %d\n", _READ_BUF_SIZE);
        fflush(stdout);
        if (NULL == fgets(read_buf, _READ_BUF_SIZE + 1, f))
        {
            return 0;
        }
        else if (read_buf[0] == term)
        {
            return 0;
        }

        uint32_t _24_bit_value = 0;

        _24_bit_value |= from_base_64(read_buf[0]) << 18;
        _24_bit_value |= from_base_64(read_buf[1]) << 12;
        _24_bit_value |= from_base_64(read_buf[2]) << 6;
        _24_bit_value |= from_base_64(read_buf[3]);

        buf[ibuf++] = (_24_bit_value >> 16) & 0xFF;
        buf[ibuf++] = (_24_bit_value >> 8) & 0xFF;
        buf[ibuf++] = (_24_bit_value) & 0xFF;

        _24_bit_value = 0;
    }
    while (true);

    return 0;
}


// ######## ##    ##  ######   #######  ########  #### ##    ##  ######
// ##       ###   ## ##    ## ##     ## ##     ##  ##  ###   ## ##    ##
// ##       ####  ## ##       ##     ## ##     ##  ##  ####  ## ##
// ######   ## ## ## ##       ##     ## ##     ##  ##  ## ## ## ##   ####
// ##       ##  #### ##       ##     ## ##     ##  ##  ##  #### ##    ##
// ##       ##   ### ##    ## ##     ## ##     ##  ##  ##   ### ##    ##
// ######## ##    ##  ######   #######  ########  #### ##    ##  ######

static const int32_t ENCODING_COOKIE    = 0x1c849308 + (8 << 4);
static const int32_t COMPRESSION_COOKIE = 0x1c849309 + (8 << 4);

#define HDR_COMPRESSION_COOKIE_MISMATCH -29999
#define HDR_ENCODING_COOKIE_MISMATCH -29998
#define HDR_DEFLATE_INIT_FAIL -29997
#define HDR_DEFLATE_FAIL -29996
#define HDR_INFLATE_INIT_FAIL -29995
#define HDR_INFLATE_FAIL -29994

const char* hdr_strerror(int errnum)
{
    switch (errnum)
    {
        case EINVAL:
            return "Invalid argument";
        case ENOMEM:
            return "Out of memory";
        case HDR_COMPRESSION_COOKIE_MISMATCH:
            return "Compression cookie mismatch";
        case HDR_ENCODING_COOKIE_MISMATCH:
            return "Encoding cookie mismatch";
        case HDR_DEFLATE_INIT_FAIL:
            return "Deflate initialisation failed";
        case HDR_DEFLATE_FAIL:
            return "Deflate failed";
        case HDR_INFLATE_INIT_FAIL:
            return "Inflate initialisation failed";
        case HDR_INFLATE_FAIL:
            return "Inflate failed";
        default:
            return "Unknown error";
    }
}

struct __attribute__((__packed__)) _encoding_flyweight
{
    int32_t cookie;
    int32_t significant_figures;
    int64_t lowest_trackable_value;
    int64_t highest_trackable_value;
    int64_t total_count;
    int64_t counts[0];
};

static void strm_init(z_stream* strm)
{
    strm->zfree = NULL;
    strm->zalloc = NULL;
    strm->opaque = NULL;
}

size_t hdr_encode(struct hdr_histogram* h, uint8_t* buffer, int length)
{
    size_t histogram_size = hdr_get_memory_size(h);

    if (histogram_size > length)
    {
        return 0;
    }

    memset((void*) buffer, 0, length);

    struct _encoding_flyweight* flyweight = (struct _encoding_flyweight*) buffer;

    flyweight->cookie                  = htobe32(ENCODING_COOKIE);
    flyweight->significant_figures     = htobe32(h->significant_figures);
    flyweight->lowest_trackable_value  = htobe64(0);
    flyweight->highest_trackable_value = htobe64(h->highest_trackable_value);
    flyweight->total_count             = htobe64(h->total_count);

    for (int i = 0; i < h->counts_len; i++)
    {
        flyweight->counts[i] = htobe64(h->counts[i]);
    }

    return 1;
}

static void do_decode(int64_t* counts, int64_t total_count, struct hdr_histogram* h)
{
    for (int i = 0; i < h->counts_len; i++)
    {
        h->counts[i] = be64toh(counts[i]);
    }

    h->total_count = total_count;
}

bool hdr_decode(uint8_t* buffer, size_t length, struct hdr_histogram** result)
{
    struct _encoding_flyweight* flyweight = (struct _encoding_flyweight*) buffer;

    if (*result == NULL)
    {
        hdr_alloc(be64toh(flyweight->highest_trackable_value),
                  be32toh(flyweight->significant_figures),
                  result);
    }
    else
    {
        return false;
    }

    struct hdr_histogram* h = *result;
    do_decode(flyweight->counts, be64toh(flyweight->total_count), h);

    return true;
}

struct __attribute__((__packed__)) _compression_flyweight
{
    int32_t cookie;
    int32_t length;
    uint8_t data[0];
};

int hdr_encode_compressed(struct hdr_histogram* h, uint8_t* buffer, int length)
{
    int ret = -1;
    uint8_t* tmp_buffer = NULL;

    if (length < sizeof(struct _compression_flyweight))
    {
        ret = EINVAL;
        goto cleanup;
    }

    memset((void*) buffer, 0, length);
    struct _compression_flyweight* flyweight = (struct _compression_flyweight*) buffer;
    size_t histogram_size = hdr_get_memory_size(h);
    tmp_buffer = (uint8_t*) calloc(histogram_size, sizeof(uint8_t));

    if (!tmp_buffer)
    {
        ret = ENOMEM;
        goto cleanup;
    }

    if (!hdr_encode(h, tmp_buffer, histogram_size))
    {
        ret = -1;
        goto cleanup;
    }

    z_stream strm;
    int level = 4;

    strm_init(&strm);
    ret = deflateInit(&strm, level);

    if (ret != Z_OK)
    {
        ret = HDR_DEFLATE_INIT_FAIL;
        goto cleanup;
    }

    strm.next_in = tmp_buffer;
    strm.avail_in = histogram_size;

    strm.next_out = (uint8_t*) &(flyweight->data);
    strm.avail_out = length - sizeof(struct _compression_flyweight);

    ret = deflate(&strm, Z_SYNC_FLUSH);
    (void) deflateEnd(&strm);

    if (ret != Z_OK)
    {
        ret = HDR_DEFLATE_FAIL;
        goto cleanup;
    }

    flyweight->cookie = htobe32(COMPRESSION_COOKIE);
    flyweight->length = htobe32(strm.total_out);

    ret = 0;

cleanup:
    if (NULL != tmp_buffer)
    {
        free(tmp_buffer);
    }

    return ret;
}

int hdr_decode_compressed(uint8_t* buffer, size_t length, struct hdr_histogram** result)
{
    int ret = -1;
    int64_t* counts_array = NULL;

    if (length < sizeof(struct _compression_flyweight) || *result != NULL)
    {
        ret = EINVAL;
        goto cleanup;
    }

    struct _compression_flyweight* compression_flyweight = (struct _compression_flyweight*) buffer;
    struct _encoding_flyweight encoding_flyweight;

    if (COMPRESSION_COOKIE != be32toh(compression_flyweight->cookie))
    {
        ret = HDR_COMPRESSION_COOKIE_MISMATCH;
        goto cleanup;
    }

    int32_t compressed_length = be32toh(compression_flyweight->length);

    memset((void*) &encoding_flyweight, 0, sizeof(struct _encoding_flyweight));

    z_stream strm;
    strm_init(&strm);

    ret = inflateInit(&strm);
    if (ret != Z_OK)
    {
        ret = HDR_INFLATE_INIT_FAIL;
        goto cleanup;
    }

    strm.next_in = buffer + sizeof(struct _compression_flyweight);
    strm.avail_in = compressed_length;
    strm.next_out = (uint8_t *) &encoding_flyweight;
    strm.avail_out = sizeof(struct _encoding_flyweight);

    ret = inflate(&strm, Z_SYNC_FLUSH);
    if (ret != Z_OK)
    {
        ret = HDR_INFLATE_FAIL;
        goto cleanup;
    }

    int64_t highest_trackable_value = be64toh(encoding_flyweight.highest_trackable_value);
    int32_t significant_figures     = be32toh(encoding_flyweight.significant_figures);

    if (hdr_alloc(highest_trackable_value, significant_figures, result) != 0)
    {
        ret = ENOMEM;
        goto cleanup;
    }

    size_t counts_size = sizeof(int64_t) * (*result)->counts_len;
    counts_array = (int64_t*) calloc((*result)->counts_len, sizeof(int64_t));
    if (NULL == counts_array)
    {
        ret = ENOMEM;
        goto cleanup;
    }

    strm.next_out = (uint8_t*) counts_array;
    strm.avail_out = counts_size;

    ret = inflate(&strm, Z_SYNC_FLUSH);
    inflateEnd(&strm);
    if (ret != Z_OK)
    {
        ret = HDR_INFLATE_FAIL;
        goto cleanup;
    }

    do_decode(counts_array, be64toh(encoding_flyweight.total_count), *result);

cleanup:
    if (NULL != counts_array)
    {
        free(counts_array);
    }

    return 0;
}

int32_t hdr_get_compressed_length(uint8_t* buffer)
{
    struct _compression_flyweight* flyweight = (struct _compression_flyweight*) buffer;
    return flyweight->length;
}
