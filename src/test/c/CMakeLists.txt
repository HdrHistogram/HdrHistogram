INCLUDE(CheckLibraryExists)

add_executable(testing hdr_histogram_test.c hdr_histogram_log_test.c hdr_dbl_histogram_test.c main_test.c)
add_executable(perftest hdr_histogram_perf.c)

target_link_libraries(testing hdr_histogram m z)
target_link_libraries(perftest hdr_histogram m z)

CHECK_LIBRARY_EXISTS(rt clock_gettime "" RT_EXISTS)
if (RT_EXISTS)
    target_link_libraries(testing rt)
    target_link_libraries(perftest rt)
endif (RT_EXISTS)

install(TARGETS testing DESTINATION bin)
install(TARGETS perftest DESTINATION bin)

add_test(Alltests testing)
