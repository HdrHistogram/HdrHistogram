/**
 * hdr_histogram_log.h
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * This code follows the Plan 9 approach to header declaration.  In order
 * to maintain fast builds does not define it's dependent headers.
 * They should be included manually by the user.  This code requires:
 *
 * - #include <stdint.h>
 * - #include <stdbool.h>
 * - #include <stdio.h>
 * - #include <time.h>
 *
 * The source for the hdr_histogram utilises a few C99 constructs, specifically
 * the use of stdint/stdbool and inline variable declaration.
 *
 * The implementation makes use of zlib to provide compression.  You will need
 * to link against -lz in order to link applications that include this header.
 */

#define HDR_COMPRESSION_COOKIE_MISMATCH -29999
#define HDR_ENCODING_COOKIE_MISMATCH -29998
#define HDR_DEFLATE_INIT_FAIL -29997
#define HDR_DEFLATE_FAIL -29996
#define HDR_INFLATE_INIT_FAIL -29995
#define HDR_INFLATE_FAIL -29994
#define HDR_LOG_INVALID_VERSION -29993


int hdr_decode_compressed(uint8_t* buffer, size_t length, struct hdr_histogram** result);

int hdr_parse_log(FILE* log, struct hdr_histogram** result);

struct hdr_log_writer
{

};

int hdr_log_writer_init(struct hdr_log_writer* writer);
int hdr_log_write_header(
    struct hdr_log_writer* writer,
    FILE* file,
    const char* user_prefix,
    struct timespec* timestamp);

int hdr_log_write(
    struct hdr_log_writer* writer,
    FILE* file,
    const struct timespec* start_timestamp,
    const struct timespec* end_timestamp,
    struct hdr_histogram* histogram);

struct hdr_log_reader
{
    int major_version;
    int minor_version;
    struct timespec start_timestamp;
};

int hdr_log_reader_init(struct hdr_log_reader* reader);
int hdr_log_read_header(struct hdr_log_reader* reader, FILE* file);
int hdr_log_read(
    struct hdr_log_reader* reader, FILE* file, struct hdr_histogram** histogram);


const char* hdr_strerror(int errnum);
