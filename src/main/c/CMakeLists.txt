
add_library(hdr_histogram SHARED hdr_histogram.c hdr_histogram_log.c hdr_dbl_histogram.c)
add_library(hdr_histogram_static STATIC hdr_histogram.c hdr_histogram_log.c hdr_dbl_histogram.c)

target_link_libraries(hdr_histogram m z)
target_link_libraries(hdr_histogram_static m z)

install(TARGETS hdr_histogram DESTINATION lib)
install(TARGETS hdr_histogram_static DESTINATION lib)
install(FILES hdr_histogram.h hdr_histogram_log.h hdr_writer_reader_phaser.h hdr_interval_recorder.h DESTINATION include/hdr)
install(DIRECTORY mintomic/include/mintomic DESTINATION include/hdr)
install(DIRECTORY mintomic/include/mintsystem DESTINATION include/hdr)

