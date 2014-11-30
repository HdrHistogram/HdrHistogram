
add_library(hdr_histogram hdr_histogram.c hdr_histogram_log.c)
install(TARGETS hdr_histogram DESTINATION lib)
install(FILES hdr_histogram.h hdr_histogram_log.h DESTINATION include)
