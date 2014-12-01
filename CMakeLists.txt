cmake_minimum_required(VERSION 2.8)

project("hdr_histogram")

if(UNIX)
    set(CMAKE_C_FLAGS "-Wall -std=gnu99")
    set(CMAKE_C_FLAGS_DEBUG "-O0 -g")
    set(CMAKE_C_FLAGS_RELEASE "-O3")
endif()

set(MINTOMIC_DIR "${CMAKE_SOURCE_DIR}/src/main/c/mintomic")

include_directories("${MINTOMIC_DIR}/include")
add_subdirectory("${MINTOMIC_DIR}/src/mintomic" mintomic)
add_subdirectory("${MINTOMIC_DIR}/src/mintsystem" mintsystem)
#add_subdirectory("${MINTOMIC_DIR}/src/mintpack" mintpack)

include_directories("${CMAKE_SOURCE_DIR}/src/main/c")
add_subdirectory("${CMAKE_SOURCE_DIR}/src/main/c")
add_subdirectory("${CMAKE_SOURCE_DIR}/src/test/c")
add_subdirectory("${CMAKE_SOURCE_DIR}/src/examples/c")

SET(CPACK_GENERATOR "TGZ")

INCLUDE(CPack)