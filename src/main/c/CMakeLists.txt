FILE(GLOB histogram_files "*.c")

add_library(hdr_histogram SHARED ${histogram_files})
add_library(hdr_histogram_static STATIC ${histogram_files})

target_link_libraries(hdr_histogram m z)
target_link_libraries(hdr_histogram_static m z)

install(TARGETS hdr_histogram DESTINATION lib)
install(TARGETS hdr_histogram_static DESTINATION lib)
install(FILES hdr_histogram.h hdr_histogram_log.h hdr_writer_reader_phaser.h hdr_interval_recorder.h DESTINATION include/hdr)
