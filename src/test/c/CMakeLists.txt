INCLUDE(CheckLibraryExists)

add_executable(hdr_histogram_test hdr_histogram_test.c)
add_executable(hdr_histogram_log_test hdr_histogram_log_test.c)
add_executable(hdr_dbl_histogram_test hdr_dbl_histogram_test.c)

add_executable(perftest hdr_histogram_perf.c)

target_link_libraries(hdr_histogram_test hdr_histogram m z)
target_link_libraries(hdr_histogram_log_test hdr_histogram m z)
target_link_libraries(hdr_dbl_histogram_test hdr_histogram m z)
target_link_libraries(perftest hdr_histogram m z)

CHECK_LIBRARY_EXISTS(rt clock_gettime "" RT_EXISTS)
if (RT_EXISTS)
    target_link_libraries(hdr_histogram_log_test rt)
    target_link_libraries(perftest rt)
endif (RT_EXISTS)

install(TARGETS hdr_histogram_test DESTINATION bin)
install(TARGETS hdr_histogram_log_test DESTINATION bin)
install(TARGETS hdr_dbl_histogram_test DESTINATION bin)
install(TARGETS perftest DESTINATION bin)

add_test(Histogram hdr_histogram_test)
add_test(HistogramLogging hdr_histogram_log_test)
add_test(DoubleHistogram hdr_dbl_histogram_test)
