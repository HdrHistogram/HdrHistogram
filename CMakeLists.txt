cmake_minimum_required(VERSION 2.8)

project("hdr_histogram")

#SET (CMAKE_C_COMPILER "clang")

if(UNIX)
    set(CMAKE_C_FLAGS "-Wall -std=gnu11")
    set(CMAKE_C_FLAGS_DEBUG "-O0 -g")
    set(CMAKE_C_FLAGS_RELEASE "-O3")
endif()

set(MINTOMIC_DIR "${CMAKE_SOURCE_DIR}/src/main/c/mintomic")

include_directories("${CMAKE_SOURCE_DIR}/src/main/c")
add_subdirectory("${CMAKE_SOURCE_DIR}/src/main/c")
add_subdirectory("${CMAKE_SOURCE_DIR}/src/test/c")
add_subdirectory("${CMAKE_SOURCE_DIR}/src/examples/c")

SET(CPACK_GENERATOR "TGZ")

INCLUDE(CPack)