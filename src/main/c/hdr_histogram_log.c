/**
 * hdr_histogram.c
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

#define _GNU_SOURCE
#include <stdlib.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <zlib.h>
#include <errno.h>
#include <sys/stat.h>
#include <ctype.h>
#include <math.h>

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

enum zero_strategy { ZERO_ALL, ZERO_NONE };

int realloc_buffer(
    void** buffer, size_t nmemb, size_t size, enum zero_strategy zeroing)
{
    int len = nmemb * size;
    if (NULL == *buffer)
    {
        *buffer = malloc(len);
    }
    else
    {
        *buffer = realloc(*buffer, len);
    }

    if (NULL == *buffer)
    {
        return ENOMEM;
    }
    else
    {
        if (zeroing == ZERO_ALL)
        {
            memset(*buffer, 0, len);
        }
        return 0;
    }
}

//  ######  ######## ########  #### ##    ##  ######    ######
// ##    ##    ##    ##     ##  ##  ###   ## ##    ##  ##    ##
// ##          ##    ##     ##  ##  ####  ## ##        ##
//  ######     ##    ########   ##  ## ## ## ##   ####  ######
//       ##    ##    ##   ##    ##  ##  #### ##    ##        ##
// ##    ##    ##    ##    ##   ##  ##   ### ##    ##  ##    ##
//  ######     ##    ##     ## #### ##    ##  ######    ######

int null_trailing_whitespace(char* s, int len)
{
    int i = len;
    while (--i != -1)
    {
        if (isspace(s[i]))
        {
            s[i] = '\0';
        }
        else
        {
            return i + 1;
        }
    }

    return 0;
}

static bool starts_with(const char* s, char c)
{
    int len = strlen(s);

    for (int i = 0; i < len; i++)
    {
        char x = s[i];

        if (!isspace(x))
        {
            return x == c;
        }
    }

    return true;
}

static bool is_comment(const char* s)
{
    return starts_with(s, '#');
}

// ########     ###     ######  ########     #######  ##
// ##     ##   ## ##   ##    ## ##          ##     ## ##    ##
// ##     ##  ##   ##  ##       ##          ##        ##    ##
// ########  ##     ##  ######  ######      ########  ##    ##
// ##     ## #########       ## ##          ##     ## #########
// ##     ## ##     ## ##    ## ##          ##     ##       ##
// ########  ##     ##  ######  ########     #######        ##

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
    uint32_t _6_bit_value = 0x3F & (_24_bit_value >> shift);
    return base64_table[_6_bit_value];
}

static int from_base_64(int c)
{
    if ('A' <= c && c <= 'Z')
    {
        return c - 'A';
    }
    else if ('a' <= c && c <= 'z')
    {
        return (c - 'a') + 26;
    }
    else if ('0' <= c && c <= '9')
    {
        return (c - '0') + 52;
    }
    else if ('+' == c)
    {
        return 62;
    }
    else if ('/' == c)
    {
        return 63;
    }
    else if ('=' == c)
    {
        return 0;
    }

    return EINVAL;
}

static size_t base64_encoded_len(size_t decoded_size)
{
    return (size_t) (ceil(decoded_size / 3.0) * 4.0);
}

static size_t base64_decoded_len(size_t encoded_size)
{
    return (encoded_size / 4) * 3;
}

void base64_encode_block_pad(const uint8_t* input, char* output, int pad)
{
    uint32_t _24_bit_value = 0;

    switch (pad)
    {
        case 2:
            _24_bit_value = (input[0] << 16) + (input[1] << 8);

            output[0] = get_base_64(_24_bit_value, 18);
            output[1] = get_base_64(_24_bit_value, 12);
            output[2] = get_base_64(_24_bit_value,  6);
            output[3] = '=';

            break;

        case 1:
            _24_bit_value = (input[0] << 16);

            output[0] = get_base_64(_24_bit_value, 18);
            output[1] = get_base_64(_24_bit_value, 12);
            output[2] = '=';
            output[3] = '=';

            break;
    }
}

/**
 * Assumes that there is 3 input bytes and 4 output chars.
 */
void base64_encode_block(const uint8_t* input, char* output)
{
    uint32_t _24_bit_value = (input[0] << 16) + (input[1] << 8) + (input[2]);

    output[0] = get_base_64(_24_bit_value, 18);
    output[1] = get_base_64(_24_bit_value, 12);
    output[2] = get_base_64(_24_bit_value,  6);
    output[3] = get_base_64(_24_bit_value,  0);
}

int base64_encode(
    const uint8_t* input, size_t input_len, char* output, size_t output_len)
{
    if (base64_encoded_len(input_len) != output_len)
    {
        return EINVAL;
    }

    int i = 0;
    int j = 0;
    for (; input_len - i >= 3 && j < output_len; i += 3, j += 4)
    {
        base64_encode_block(&input[i], &output[j]);
    }

    int remaining = input_len - i;

    base64_encode_block_pad(&input[i], &output[j], remaining);

    return 0;
}

/**
 * Assumes that there is 4 input chars available and 3 output chars.
 */
void base64_decode_block(const char* input, uint8_t* output)
{
    uint32_t _24_bit_value = 0;

    _24_bit_value |= from_base_64(input[0]) << 18;
    _24_bit_value |= from_base_64(input[1]) << 12;
    _24_bit_value |= from_base_64(input[2]) << 6;
    _24_bit_value |= from_base_64(input[3]);

    output[0] = (uint8_t) ((_24_bit_value >> 16) & 0xFF);
    output[1] = (uint8_t) ((_24_bit_value >> 8) & 0xFF);
    output[2] = (uint8_t) ((_24_bit_value) & 0xFF);
}

int base64_decode(
    const char* input, size_t input_len, uint8_t* output, size_t output_len)
{
    if (input_len < 4 ||
        (input_len & 3) != 0 ||
        (input_len / 4) * 3 != output_len)
    {
        return EINVAL;
    }

    for (int i = 0, j = 0; i < input_len; i += 4, j += 3)
    {
        base64_decode_block(&input[i], &output[j]);
    }

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
#define HDR_DEFLATE_NEED_REALLOC -29993

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
        case HDR_DEFLATE_NEED_REALLOC:
            return "Deflate - need to realloc buffer";
        default:
            return "Unknown error";
    }
}

static void strm_init(z_stream* strm)
{
    strm->zfree = NULL;
    strm->zalloc = NULL;
    strm->opaque = NULL;
    strm->next_in = NULL;
    strm->avail_in = 0;
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

struct __attribute__((__packed__)) _compression_flyweight
{
    int32_t cookie;
    int32_t length;
    uint8_t data[0];
};

int hdr_encode_compressed(struct hdr_histogram* h, uint8_t** buffer, int* length)
{
    const int counts_per_chunk = 512;
    const int chunk_len =
        sizeof(struct _encoding_flyweight) + counts_per_chunk * sizeof(int64_t);
    uint8_t chunk[chunk_len];
    int buffer_len = 4096;

    int ret = 0;
    int r;
    z_stream strm;
    strm_init(&strm);

    if (NULL == *buffer)
    {
        if ((*buffer = (uint8_t*) calloc(buffer_len, sizeof(uint8_t))) == NULL)
        {
            ret = ENOMEM;
            goto cleanup;
        }
    }
    else
    {
        buffer_len = *length;
    }

    struct _compression_flyweight* comp_fw = (struct _compression_flyweight*) *buffer;

    comp_fw->cookie = htobe32(COMPRESSION_COOKIE);
    comp_fw->length = 0; // Figure this out later.


    if (deflateInit(&strm, 4) != Z_OK)
    {
        ret = HDR_DEFLATE_INIT_FAIL;
        goto cleanup;
    }

    struct _encoding_flyweight* encode_fw = (struct _encoding_flyweight*) chunk;

    encode_fw->cookie                  = htobe32(ENCODING_COOKIE);
    encode_fw->significant_figures     = htobe32(h->significant_figures);
    encode_fw->lowest_trackable_value  = htobe64(h->lowest_trackable_value);
    encode_fw->highest_trackable_value = htobe64(h->highest_trackable_value);
    encode_fw->total_count             = htobe64(h->total_count);

    int counts_index = 0;

    strm.next_in = chunk;
    strm.avail_in = chunk_len;

    strm.next_out = (Bytef*) &comp_fw->data;
    strm.avail_out = buffer_len - sizeof(struct _compression_flyweight);

    do
    {
        for (int i = 0; i < counts_per_chunk && counts_index < h->counts_len; i++)
        {
            encode_fw->counts[i] = htobe64(h->counts[counts_index++]);
        }

        r = deflate(&strm, Z_NO_FLUSH);

        if (r != Z_OK && r != Z_STREAM_END)
        {
            ret = HDR_DEFLATE_FAIL;
            goto cleanup;
        }

        strm.next_in = (Bytef*) &encode_fw->counts;
        strm.avail_in = counts_per_chunk * sizeof(int64_t);

        if (strm.avail_out == 0)
        {
            printf("Reallocating in loop\n");

            // Reallocate to doubled buffer.
            size_t new_len = buffer_len * 2;
            *buffer = (uint8_t*) realloc(*buffer, new_len * sizeof(uint8_t));
            if (NULL == *buffer)
            {
                ret = ENOMEM;
                goto cleanup;
            }
            strm.next_out = &(*buffer)[buffer_len];
            memset(strm.next_out, 0, buffer_len * sizeof(uint8_t));
            strm.avail_out = buffer_len;
            buffer_len = new_len;
            comp_fw = (struct _compression_flyweight*) *buffer;
        }
    }
    while (counts_index < h->counts_len);

    do
    {
        r = deflate(&strm, Z_FINISH);

        if (strm.avail_out == 0)
        {
            printf("Reallocating in finish\n");
            // Reallocate to doubled buffer.
            size_t new_len = buffer_len * 2;
            *buffer = (uint8_t*) realloc(*buffer, new_len * sizeof(uint8_t));
            if (NULL == *buffer)
            {
                ret = ENOMEM;
                goto cleanup;
            }
            strm.next_out = &(*buffer)[buffer_len];
            memset(strm.next_out, 0, buffer_len * sizeof(uint8_t));
            strm.avail_out = buffer_len;
            buffer_len = new_len;
            comp_fw = (struct _compression_flyweight*) *buffer;
        }
    }
    while (r != Z_STREAM_END);

    comp_fw->cookie = htobe32(COMPRESSION_COOKIE);
    comp_fw->length = htobe32(strm.total_out);
    *length = sizeof(struct _compression_flyweight) + comp_fw->length;

cleanup:
    (void)deflateEnd(&strm);

    return ret;
}

int hdr_decode_compressed(uint8_t* buffer, size_t length, struct hdr_histogram** result)
{
    const int counts_per_chunk = 512;
    int ret = 0;
    int64_t counts_array[counts_per_chunk];
    z_stream strm;
    strm_init(&strm);
    struct hdr_histogram* h;

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

    if (inflateInit(&strm) != Z_OK)
    {
        ret = HDR_INFLATE_INIT_FAIL;
        goto cleanup;
    }

    strm.next_in = compression_flyweight->data;
    strm.avail_in = compressed_length;
    strm.next_out = (uint8_t *) &encoding_flyweight;
    strm.avail_out = sizeof(struct _encoding_flyweight);

    if (inflate(&strm, Z_SYNC_FLUSH) != Z_OK)
    {
        ret = HDR_INFLATE_FAIL;
        goto cleanup;
    }

    if (ENCODING_COOKIE != be32toh(encoding_flyweight.cookie))
    {
        ret = HDR_ENCODING_COOKIE_MISMATCH;
        goto cleanup;
    }

    int64_t lowest_trackable_value = be64toh(encoding_flyweight.lowest_trackable_value);
    int64_t highest_trackable_value = be64toh(encoding_flyweight.highest_trackable_value);
    int32_t significant_figures = be32toh(encoding_flyweight.significant_figures);

    if (hdr_init(
        lowest_trackable_value,
        highest_trackable_value,
        significant_figures,
        &h) != 0)
    {
        ret = ENOMEM;
        goto cleanup;
    }

    h->total_count = be64toh(encoding_flyweight.total_count);

    int counts_index = 0;
    int available_counts = 0;
    int inflate_res = 0;
    do
    {
        strm.next_out = (uint8_t*) counts_array;
        strm.avail_out = counts_per_chunk * sizeof(int64_t);

        inflate_res = inflate(&strm, Z_NO_FLUSH);

        if (Z_STREAM_END != inflate_res && Z_OK != inflate_res)
        {
            ret = HDR_INFLATE_FAIL;
            break;
        }

        available_counts = counts_per_chunk - (strm.avail_out / sizeof(int64_t));
        for (int i = 0; i < available_counts && counts_index < h->counts_len; i++)
        {
            h->counts[counts_index++] = be64toh(counts_array[i]);
        }
    }
    while (inflate_res == Z_OK);

cleanup:
    (void)inflateEnd(&strm);

    if (ret != 0)
    {
        free(h);
    }
    else
    {
        *result = h;
    }

    return ret;
}

struct _log_header
{
    int major_version;
    int minor_version;
    int64_t start_time_ms;
};

static int scan_log_format(const char* line, struct _log_header* header)
{
    const char* format = "#[Histogram log format version %d.%d]";
    return sscanf(line, format, &header->major_version, &header->minor_version);
}

static void scan_start_time(const char* line, struct _log_header* header)
{
    const char* format = "#[StartTime: %d.%d [^\n]";
    int64_t timestamp_s = 0;
    int64_t trailing_ms = 0;
    if (sscanf(line, format, &timestamp_s, &trailing_ms) == 2)
    {
        header->start_time_ms = (timestamp_s * 1000) + trailing_ms;
    }
}

static void parse_log_comments(FILE* f, struct _log_header* header)
{
    char buf[4096];

    header->major_version = 0;
    header->minor_version = 0;
    header->start_time_ms = 0;

    do
    {
        char* line = fgets(buf, 4096, f);

        if (NULL != line && is_comment(line))
        {
            scan_log_format(line, header);
            scan_start_time(line, header);
        }
        else
        {
            break;
        }
    }
    while (true);
}

int parse_lines(FILE* f, struct hdr_histogram** result)
{
    const char* format = "%d.%d,%d.%d,%d.%d,%s";
    char* base64_histogram = NULL;
    uint8_t* compressed_histogram = NULL;
    char* line;
    size_t line_len;
    int ret = 0;

    do
    {
        int begin_s = 0;
        int begin_ms = 0;
        int end_s = 0;
        int end_ms = 0;
        int interval_max_s = 0;
        int interval_max_ms = 0;

        int read = getline(&line, &line_len, f);
        if (read == -1)
        {
            break;
        }

        int r;
        r = realloc_buffer(
            (void**)&base64_histogram, sizeof(char), read, ZERO_ALL);
        if (r != 0)
        {
            ret = ENOMEM;
            goto cleanup;
        }

        r = realloc_buffer(
            (void**)&compressed_histogram, sizeof(uint8_t), read, ZERO_ALL);
        if (r != 0)
        {
            ret = ENOMEM;
            goto cleanup;
        }

        int res = sscanf(
            line, format, &begin_s, &begin_ms, &end_s, &end_ms,
            &interval_max_s, &interval_max_ms, base64_histogram);

        if (res > 0)
        {
            null_trailing_whitespace(base64_histogram, strlen(base64_histogram));

            int base64_len = strlen(base64_histogram);
            int compressed_len = base64_decoded_len(base64_len);

            int base64_res = base64_decode(
                base64_histogram, base64_len,
                compressed_histogram, compressed_len);

            if (base64_res != 0)
            {
                ret = base64_res;
                goto cleanup;
            }

            struct hdr_histogram* h = NULL;
            if (hdr_decode_compressed(compressed_histogram, compressed_len, &h) == 0)
            {
                hdr_percentiles_print(h, stdout, 5, 1.0, CSV);
            }
        }
        else
        {
            break;
        }
    }
    while (true);

cleanup:
    free(line);
    free(base64_histogram);
    free(compressed_histogram);

    return ret;
}

int hdr_parse_log(FILE* f, struct hdr_histogram** result)
{
    struct _log_header header;

    parse_log_comments(f, &header);

    // printf(
    //     "Version: %d.%d, at: %ld\n",
    //     header.major_version,
    //     header.minor_version,
    //     header.start_time_ms);

    // TODO: Check log file version.

    // The CSV Header will be consumed in the parsing of the log comments

    parse_lines(f, result);

    return 0;
}

