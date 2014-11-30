cmake_minimum_required(VERSION 2.8)

project("hdr_histogram")

if(UNIX)
    set(CMAKE_C_FLAGS "-Wall -std=gnu99")
    set(CMAKE_C_FLAGS_DEBUG "-O0 -g")
    set(CMAKE_C_FLAGS_RELEASE "-O3")
endif()

include_directories("${CMAKE_SOURCE_DIR}/src/main/c")
add_subdirectory("${CMAKE_SOURCE_DIR}/src/main/c")
add_subdirectory("${CMAKE_SOURCE_DIR}/src/test/c")
add_subdirectory("${CMAKE_SOURCE_DIR}/src/examples/c")

#install(TARGETS hdr_histogram DESTINATION lib)
#install(TARGETS hdr_histogram.h hdr_histogram_log.h DESTINATION include)