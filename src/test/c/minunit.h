 /* file: minunit.h */
struct mu_result
{
    char* test;
    char* message;
};

#define mu_assert(message, test) \
    do {                         \
        if (!(test))             \
            return message;      \
    } while (0)

#define mu_run_test(name)        \
    do {                         \
        char *message = name();  \
        tests_run++;             \
        if (message) {           \
            struct mu_result r;  \
            r.test = #name;      \
            r.message = message; \
            return r;            \
        }                        \
    } while (0)

#define mu_ok               \
    do {                    \
        struct mu_result r; \
        r.test = 0;         \
        r.message = 0;      \
        return r;           \
    } while (0)

 extern int tests_run;
