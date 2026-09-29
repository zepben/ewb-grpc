cmake_minimum_required(VERSION 3.24)

project(zepben-protobuf VERSION 1.8.0 LANGUAGES CXX)

include(CMakePackageConfigHelpers)

find_package(Protobuf CONFIG REQUIRED)
find_package(gRPC CONFIG REQUIRED)

set(PROTO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../proto")
set(GENERATED_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/src")
file(MAKE_DIRECTORY "${GENERATED_ROOT}")

file(GLOB_RECURSE PROTO_FILES CONFIGURE_DEPENDS "${PROTO_ROOT}/*.proto")

# Only service definitions need gRPC stubs. Generating them separately avoids
# expecting .grpc.pb.* files for message-only proto definitions.
set(GRPC_PROTO_FILES)
foreach(PROTO_FILE IN LISTS PROTO_FILES)
    file(READ "${PROTO_FILE}" PROTO_CONTENT)
    string(REGEX MATCH "(^|\n)[ \t]*service[ \t]+" HAS_SERVICE "${PROTO_CONTENT}")
    if(HAS_SERVICE)
        list(APPEND GRPC_PROTO_FILES "${PROTO_FILE}")
    endif()
endforeach()

add_library(zepben_protobuf STATIC)
add_library(zepben::protobuf ALIAS zepben_protobuf)
set_target_properties(zepben_protobuf PROPERTIES
    EXPORT_NAME protobuf
    OUTPUT_NAME zepben-protobuf
)

target_compile_features(zepben_protobuf PUBLIC cxx_std_17)
target_include_directories(zepben_protobuf
    PUBLIC
        $<BUILD_INTERFACE:${GENERATED_ROOT}>
        $<INSTALL_INTERFACE:include>
)
target_link_libraries(zepben_protobuf
    PUBLIC
        protobuf::libprotobuf
        gRPC::grpc++
)

protobuf_generate(
    OUT_VAR PROTOBUF_GENERATED_SOURCES
    LANGUAGE cpp
    PROTOC_OUT_DIR "${GENERATED_ROOT}"
    IMPORT_DIRS "${PROTO_ROOT}"
    PROTOS ${PROTO_FILES}
)
target_sources(zepben_protobuf PRIVATE ${PROTOBUF_GENERATED_SOURCES})

protobuf_generate(
    OUT_VAR GRPC_GENERATED_SOURCES
    LANGUAGE grpc
    GENERATE_EXTENSIONS .grpc.pb.h .grpc.pb.cc
    PLUGIN "protoc-gen-grpc=$<TARGET_FILE:gRPC::grpc_cpp_plugin>"
    PROTOC_OUT_DIR "${GENERATED_ROOT}"
    IMPORT_DIRS "${PROTO_ROOT}"
    PROTOS ${GRPC_PROTO_FILES}
)
target_sources(zepben_protobuf PRIVATE ${GRPC_GENERATED_SOURCES})

configure_package_config_file(
    "${CMAKE_CURRENT_SOURCE_DIR}/cmake/zepben-protobuf-config.cmake.in"
    "${CMAKE_CURRENT_BINARY_DIR}/zepben-protobuf-config.cmake"
    INSTALL_DESTINATION lib/cmake/zepben-protobuf
)
write_basic_package_version_file(
    "${CMAKE_CURRENT_BINARY_DIR}/zepben-protobuf-config-version.cmake"
    VERSION "${PROJECT_VERSION}"
    COMPATIBILITY SameMajorVersion
)

install(TARGETS zepben_protobuf
    EXPORT zepben-protobuf-targets
    ARCHIVE DESTINATION lib
    LIBRARY DESTINATION lib
    RUNTIME DESTINATION bin
)
install(DIRECTORY "${GENERATED_ROOT}/"
    DESTINATION include
    FILES_MATCHING PATTERN "*.h"
)
install(EXPORT zepben-protobuf-targets
    FILE zepben-protobuf-targets.cmake
    NAMESPACE zepben::
    DESTINATION lib/cmake/zepben-protobuf
)
install(FILES
    "${CMAKE_CURRENT_BINARY_DIR}/zepben-protobuf-config.cmake"
    "${CMAKE_CURRENT_BINARY_DIR}/zepben-protobuf-config-version.cmake"
    DESTINATION lib/cmake/zepben-protobuf
)
