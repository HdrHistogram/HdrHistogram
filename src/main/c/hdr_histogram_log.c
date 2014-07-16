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
#include <time.h>

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

#define FAIL_AND_CLEANUP(label, error_name, error) \
    do                      \
    {                       \
        error_name = error; \
        goto label;         \
    }                       \
    while (0)

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

const char* hdr_strerror(int errnum)
{
    switch (errnum)
    {
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
        case HDR_LOG_INVALID_VERSION:
            return "Log - invalid version in log header";
        default:
            return strerror(errnum);
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

typedef struct __attribute__((__packed__))
{
    int32_t cookie;
    int32_t significant_figures;
    int64_t lowest_trackable_value;
    int64_t highest_trackable_value;
    int64_t total_count;
    int64_t counts[0];
} _encoding_flyweight;

typedef struct __attribute__((__packed__))
{
    int32_t cookie;
    int32_t length;
    uint8_t data[0];
} _compression_flyweight;

int hdr_encode_compressed(
    struct hdr_histogram* h,
    uint8_t** compressed_histogram,
    int* compressed_len)
{
    const int counts_per_chunk = 512;
    int64_t chunk[counts_per_chunk];

    uint8_t* buf = NULL;
    int len = 4096;

    int result = 0;
    int r;

    z_stream strm;
    strm_init(&strm);

    if ((buf = (uint8_t*) malloc(len * sizeof(uint8_t))) == NULL)
    {
        FAIL_AND_CLEANUP(cleanup, result, ENOMEM);
    }

    if (deflateInit(&strm, 4) != Z_OK)
    {
        result = HDR_DEFLATE_INIT_FAIL;
        goto cleanup;
    }

    _compression_flyweight* comp_fw = (_compression_flyweight*) buf;
    _encoding_flyweight encode_fw;

    encode_fw.cookie                  = htobe32(ENCODING_COOKIE);
    encode_fw.significant_figures     = htobe32(h->significant_figures);
    encode_fw.lowest_trackable_value  = htobe64(h->lowest_trackable_value);
    encode_fw.highest_trackable_value = htobe64(h->highest_trackable_value);
    encode_fw.total_count             = htobe64(h->total_count);

    int counts_index = 0;

    strm.next_in = (Bytef*) &encode_fw;
    strm.avail_in = sizeof(_encoding_flyweight);

    strm.next_out = (Bytef*) &comp_fw->data;
    strm.avail_out = len - sizeof(_compression_flyweight);

    if (deflate(&strm, Z_NO_FLUSH) != Z_OK)
    {
        FAIL_AND_CLEANUP(cleanup, result, HDR_DEFLATE_FAIL);
    }

    do
    {
        while (strm.avail_out == 0)
        {
            // Reallocate to doubled buffer.
            int new_len = len * 2;
            uint8_t* new_buf = (uint8_t*) realloc(buf, new_len * sizeof(uint8_t));
            if (NULL == new_buf)
            {
                FAIL_AND_CLEANUP(cleanup, result, ENOMEM);
            }

            buf = new_buf;
            strm.next_out = &buf[len];
            strm.avail_out = len;
            len = new_len;

            // Flush the zlib stream.  Breaks without this.
            if (deflate(&strm, Z_SYNC_FLUSH) != Z_OK)
            {
                FAIL_AND_CLEANUP(cleanup, result, HDR_DEFLATE_FAIL);
            }
        }

        int i = 0;
        while (i < counts_per_chunk && counts_index < h->counts_len)
        {
            chunk[i++] = htobe64(h->counts[counts_index++]);
        }

        strm.next_in = (Bytef*) chunk;
        strm.avail_in = i * sizeof(int64_t);

        int flush = i == 0 ? Z_FINISH : Z_NO_FLUSH;
        r = deflate(&strm, flush);
        if (r != Z_OK && r != Z_STREAM_END)
        {
            FAIL_AND_CLEANUP(cleanup, result, HDR_DEFLATE_FAIL);
        }
    }
    while (r != Z_STREAM_END);

    comp_fw = (_compression_flyweight*) buf;
    comp_fw->cookie = htobe32(COMPRESSION_COOKIE);
    comp_fw->length = htobe32(strm.total_out);
    *compressed_histogram = buf;
    *compressed_len = sizeof(_compression_flyweight) + strm.total_out;

cleanup:
    (void)deflateEnd(&strm);
    if (result != 0)
    {
        free(buf);
    }

    return result;
}

// ########  ########  ######   #######  ########  #### ##    ##  ######
// ##     ## ##       ##    ## ##     ## ##     ##  ##  ###   ## ##    ##
// ##     ## ##       ##       ##     ## ##     ##  ##  ####  ## ##
// ##     ## ######   ##       ##     ## ##     ##  ##  ## ## ## ##   ####
// ##     ## ##       ##       ##     ## ##     ##  ##  ##  #### ##    ##
// ##     ## ##       ##    ## ##     ## ##     ##  ##  ##   ### ##    ##
// ########  ########  ######   #######  ########  #### ##    ##  ######

int hdr_decode_compressed(
    uint8_t* buffer, size_t length, struct hdr_histogram** histogram)
{
    const int counts_per_chunk = 512;
    int64_t counts_array[counts_per_chunk];
    struct hdr_histogram* h = NULL;
    int result = 0;

    z_stream strm;
    strm_init(&strm);

    int64_t counts_tally = 0;

    if (length < sizeof(_compression_flyweight))
    {
        FAIL_AND_CLEANUP(cleanup, result, EINVAL);
    }

    _compression_flyweight* compression_flyweight = (_compression_flyweight*) buffer;
    _encoding_flyweight encoding_flyweight;

    if (COMPRESSION_COOKIE != be32toh(compression_flyweight->cookie))
    {
        FAIL_AND_CLEANUP(cleanup, result, HDR_COMPRESSION_COOKIE_MISMATCH);
    }

    int32_t compressed_length = be32toh(compression_flyweight->length);

    if (inflateInit(&strm) != Z_OK)
    {
        FAIL_AND_CLEANUP(cleanup, result, HDR_INFLATE_FAIL);
    }

    strm.next_in = compression_flyweight->data;
    strm.avail_in = compressed_length;
    strm.next_out = (uint8_t *) &encoding_flyweight;
    strm.avail_out = sizeof(_encoding_flyweight);

    if (inflate(&strm, Z_SYNC_FLUSH) != Z_OK)
    {
        FAIL_AND_CLEANUP(cleanup, result, HDR_INFLATE_FAIL);
    }

    if (ENCODING_COOKIE != be32toh(encoding_flyweight.cookie))
    {
        FAIL_AND_CLEANUP(cleanup, result, HDR_ENCODING_COOKIE_MISMATCH);
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
        FAIL_AND_CLEANUP(cleanup, result, ENOMEM);
    }

    h->total_count = be64toh(encoding_flyweight.total_count);

    int counts_index = 0;
    int available_counts = 0;
    int r = 0;
    do
    {
        strm.next_out = (uint8_t*) counts_array;
        strm.avail_out = counts_per_chunk * sizeof(int64_t);

        r = inflate(&strm, Z_SYNC_FLUSH);

        if (Z_STREAM_END != r && Z_OK != r)
        {
            FAIL_AND_CLEANUP(cleanup, result, HDR_INFLATE_FAIL);
        }

        available_counts = counts_per_chunk - (strm.avail_out / sizeof(int64_t));
        for (int i = 0; i < available_counts && counts_index < h->counts_len; i++)
        {
            h->counts[counts_index++] = be64toh(counts_array[i]);
            counts_tally += h->counts[counts_index - 1];
        }
    }
    while (r == Z_OK);

cleanup:
    (void)inflateEnd(&strm);

    if (result != 0)
    {
        free(h);
    }
    else if (NULL == *histogram)
    {
        *histogram = h;
    }
    else
    {
        hdr_add(*histogram, h);
        free(h);
    }

    return result;
}

// ##      ## ########  #### ######## ######## ########
// ##  ##  ## ##     ##  ##     ##    ##       ##     ##
// ##  ##  ## ##     ##  ##     ##    ##       ##     ##
// ##  ##  ## ########   ##     ##    ######   ########
// ##  ##  ## ##   ##    ##     ##    ##       ##   ##
// ##  ##  ## ##    ##   ##     ##    ##       ##    ##
//  ###  ###  ##     ## ####    ##    ######## ##     ##

int hdr_log_writer_init(struct hdr_log_writer* writer)
{
    return 0;
}

#define LOG_VERION "1.01"
#define LOG_MAJOR_VERSION 1
#define LOG_MINOR_VERSION 1

static int print_user_prefix(FILE* f, const char* prefix)
{
    if (!prefix)
    {
        return 0;
    }

    return fprintf(f, "#[%s]\n", prefix);
}

static int print_version(FILE* f, const char* version)
{
    return fprintf(f, "#[Histogram log format version %s]\n", version);
}

static int print_time(FILE* f, struct timespec* timestamp)
{
    char time_str[128];
    struct tm date_time;

    if (!timestamp)
    {
        return 0;
    }

    gmtime_r(&timestamp->tv_sec, &date_time);
    long ms = timestamp->tv_nsec / 1000000;
    strftime(time_str, 128, "%a %b %X %Z %Y", &date_time);

    return fprintf(
        f, "#[StartTime: %d.%ld (seconds since epoch), %s]\n",
        (int) timestamp->tv_sec, ms, time_str);
}

static int print_header(FILE* f)
{
    return fprintf(f, "\"StartTimestamp\",\"EndTimestamp\",\"Interval_Max\",\"Interval_Compressed_Histogram\"\n");
}

// Example log
// #[Logged with jHiccup version 2.0.3-SNAPSHOT]
// #[Histogram log format version 1.01]
// #[StartTime: 1403476110.183 (seconds since epoch), Mon Jun 23 10:28:30 NZST 2014]
// "StartTimestamp","EndTimestamp","Interval_Max","Interval_Compressed_Histogram"
int hdr_log_write_header(
    struct hdr_log_writer* writer, FILE* file,
    const char* user_prefix, struct timespec* timestamp)
{
    if (print_user_prefix(file, user_prefix) < 0)
    {
        return EIO;
    }
    if (print_version(file, LOG_VERION) < 0)
    {
        return EIO;
    }
    if (print_time(file, timestamp) < 0)
    {
        return EIO;
    }
    if (print_header(file) < 0)
    {
        return EIO;
    }

    return 0;
}

int hdr_log_write(
    struct hdr_log_writer* writer,
    FILE* file,
    const struct timespec* start_timestamp,
    const struct timespec* end_timestamp,
    struct hdr_histogram* histogram)
{
    uint8_t* compressed_histogram = NULL;
    int compressed_len = 0;
    char* encoded_histogram = NULL;
    int rc = 0;
    int result = 0;
    size_t encoded_len;

    rc = hdr_encode_compressed(histogram, &compressed_histogram, &compressed_len);
    if (rc != 0)
    {
        FAIL_AND_CLEANUP(cleanup, result, rc);
    }

    encoded_len = base64_encoded_len(compressed_len);
    encoded_histogram = calloc(encoded_len + 1, sizeof(char));

    rc = base64_encode(
        compressed_histogram, compressed_len, encoded_histogram, encoded_len);
    if (rc != 0)
    {
        FAIL_AND_CLEANUP(cleanup, result, rc);
    }

    if (fprintf(
        file, "%d.%d,%d.%d,%ld.0,%s\n",
        (int) start_timestamp->tv_sec, (int) (start_timestamp->tv_nsec / 1000000),
        (int) end_timestamp->tv_sec, (int) (end_timestamp->tv_nsec / 1000000),
        hdr_max(histogram),
        encoded_histogram) < 0)
    {
        result = EIO;
    }

cleanup:
    free(compressed_histogram);
    free(encoded_histogram);

    return result;
}

// ########  ########    ###    ########  ######## ########
// ##     ## ##         ## ##   ##     ## ##       ##     ##
// ##     ## ##        ##   ##  ##     ## ##       ##     ##
// ########  ######   ##     ## ##     ## ######   ########
// ##   ##   ##       ######### ##     ## ##       ##   ##
// ##    ##  ##       ##     ## ##     ## ##       ##    ##
// ##     ## ######## ##     ## ########  ######## ##     ##

int hdr_log_reader_init(struct hdr_log_reader* reader)
{
    reader->major_version = 0;
    reader->minor_version = 0;
    reader->start_timestamp.tv_sec = 0;
    reader->start_timestamp.tv_nsec = 0;

    return 0;
}

static void scan_log_format(struct hdr_log_reader* reader, const char* line)
{
    const char* format = "#[Histogram log format version %d.%d]";
    sscanf(line, format, &reader->major_version, &reader->minor_version);
}

static void scan_start_time(struct hdr_log_reader* reader, const char* line)
{
    const char* format = "#[StartTime: %d.%d [^\n]";
    int timestamp_s = 0;
    int trailing_ms = 0;

    if (sscanf(line, format, &timestamp_s, &trailing_ms) == 2)
    {
        reader->start_timestamp.tv_sec = timestamp_s;
        reader->start_timestamp.tv_nsec = trailing_ms * 1000000;
    }
}

static void scan_header_line(struct hdr_log_reader* reader, const char* line)
{
    scan_log_format(reader, line);
    scan_start_time(reader, line);
}

#define HEADER_LINE_LENGTH 128

int hdr_log_read_header(struct hdr_log_reader* reader, FILE* file)
{
    char line[HEADER_LINE_LENGTH]; // TODO: check for overflow.

    bool parsing_header = true;

    do
    {
        int c = fgetc(file);
        ungetc(c, file);

        switch (c)
        {

        case '#':
            if (fgets(line, HEADER_LINE_LENGTH, file) == NULL)
            {
                return EIO;
            }

            scan_header_line(reader, line);
            break;

        case '"':
            if (fgets(line, HEADER_LINE_LENGTH, file) == NULL)
            {
                return EIO;
            }

            parsing_header = false;
            break;

        default:
            parsing_header = false;
        }
    }
    while (parsing_header);

    if (LOG_MAJOR_VERSION != reader->major_version ||
        LOG_MINOR_VERSION != reader->minor_version)
    {
        return HDR_LOG_INVALID_VERSION;
    }

    return 0;
}

static void update_timespec(struct timespec* ts, int time_s, int time_ms)
{
    if (NULL == ts)
    {
        return;
    }

    ts->tv_sec = time_s;
    ts->tv_nsec = time_ms * 1000000;
}

int hdr_log_read(
    struct hdr_log_reader* reader, FILE* file, struct hdr_histogram** histogram,
    struct timespec* timestamp, struct timespec* interval)
{
    const char* format = "%d.%d,%d.%d,%d.%d,%s";
    char* base64_histogram = NULL;
    uint8_t* compressed_histogram = NULL;
    char* line = NULL;
    size_t line_len = 0;
    int result = 0;

    int begin_s = 0;
    int begin_ms = 0;
    int end_s = 0;
    int end_ms = 0;
    int interval_max_s = 0;
    int interval_max_ms = 0;

    int read = getline(&line, &line_len, file);
    if (read == -1)
    {
        FAIL_AND_CLEANUP(cleanup, result, EIO);
    }

    null_trailing_whitespace(line, read);
    if (strlen(line) == 0)
    {
        FAIL_AND_CLEANUP(cleanup, result, EOF);
    }

    int r;
    r = realloc_buffer(
        (void**)&base64_histogram, sizeof(char), read, ZERO_ALL);
    if (r != 0)
    {
        FAIL_AND_CLEANUP(cleanup, result, ENOMEM);
    }

    r = realloc_buffer(
        (void**)&compressed_histogram, sizeof(uint8_t), read, ZERO_ALL);
    if (r != 0)
    {
        FAIL_AND_CLEANUP(cleanup, result, ENOMEM);
    }

    int num_tokens = sscanf(
        line, format, &begin_s, &begin_ms, &end_s, &end_ms,
        &interval_max_s, &interval_max_ms, base64_histogram);

    if (num_tokens != 7)
    {
        FAIL_AND_CLEANUP(cleanup, result, EINVAL);
    }

    int base64_len = strlen(base64_histogram);
    int compressed_len = base64_decoded_len(base64_len);

    r = base64_decode(
        base64_histogram, base64_len, compressed_histogram, compressed_len);

    if (r != 0)
    {
        FAIL_AND_CLEANUP(cleanup, result, r);
    }

    r = hdr_decode_compressed(compressed_histogram, compressed_len, histogram);
    if (r != 0)
    {
        FAIL_AND_CLEANUP(cleanup, result, r);
    }

    update_timespec(timestamp, begin_s, begin_ms);
    update_timespec(interval, end_s, end_ms);

cleanup:
    free(line);
    free(base64_histogram);
    free(compressed_histogram);

    return result;
}
