# declarations for functions we actually use from the libc, taken from POSIX documentation.
# This is meant to avoid using the headers themselves, and hopefully not depend on msys2 on windows.

let stdio =
    include
        """"#include <stddef.h>

            #define SEEK_SET    0
            #define SEEK_CUR    1
            #define SEEK_END    2
            typedef struct _IO_FILE FILE;

            FILE *fopen(const char *pathname, const char *mode);
            size_t fread(void *ptr, size_t size, size_t nmemb, FILE *stream);
            size_t fwrite(const void *ptr, size_t size, size_t nmemb, FILE *stream);
            int fclose(FILE *stream);
            int fseek(FILE *stream, long offset, int whence);
            long ftell(FILE *stream);
            void rewind(FILE *stream);
            char *fgets(char *restrict s, int n, FILE *restrict stream);

            int printf(const char *restrict format, ...);
            int sscanf(const char *restrict s, const char *restrict format, ... );

let _string =
    include
        """"#include <stddef.h>
            void *memcpy(void *restrict, const void *restrict, size_t);

do
    let stdio =
        ..
            stdio.extern
            stdio.define

    let string =
        ..
            _string.extern
            _string.define

    locals;
