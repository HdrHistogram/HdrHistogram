add_executable(hdr_decoder hdr_decoder.c)
target_link_libraries(hdr_decoder hdr_histogram m z)
install(TARGETS hdr_decoder DESTINATION bin)

if (${CMAKE_SYSTEM_NAME} MATCHES "Linux")
    add_executable(hiccup hiccup.c)
    target_link_libraries(hiccup hdr_histogram m z pthread)
    install(TARGETS hiccup DESTINATION bin)
endif(${CMAKE_SYSTEM_NAME} MATCHES "Linux")
