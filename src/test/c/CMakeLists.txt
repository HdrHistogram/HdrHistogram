

add_executable(testing hdr_histogram_test.c hdr_histogram_log_test.c main_test.c)
target_link_libraries(testing hdr_histogram m rt z)
install(TARGETS testing DESTINATION bin)

add_executable(perftest hdr_histogram_perf.c)
target_link_libraries(perftest hdr_histogram m rt z)
install(TARGETS perftest DESTINATION bin)
