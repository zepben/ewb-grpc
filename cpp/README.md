# EWB C++ protobuf stubs

This module generates C++ protobuf message types for every definition in
`../proto` and C++ gRPC client/server stubs for every service definition. It is
the C++ counterpart to the Java Maven build and `python/build.py`.

## Prerequisites

- CMake 3.24 or later
- Protobuf development files, including `protoc`
- gRPC development files, including `grpc_cpp_plugin`

The Protobuf and gRPC versions must be compatible.

## Build

From the repository root:

```sh
cmake -S cpp -B cpp/build
cmake --build cpp/build
```

Generated headers and sources are placed in `cpp/src`, retaining
their proto import paths. For example, `zepben/protobuf/ec/ec.proto` produces:

```text
cpp/src/zepben/protobuf/ec/ec.pb.h
cpp/src/zepben/protobuf/ec/ec.pb.cc
cpp/src/zepben/protobuf/ec/ec.grpc.pb.h
cpp/src/zepben/protobuf/ec/ec.grpc.pb.cc
```

The build produces the `zepben-protobuf` static library. To install it and its
generated headers:

```sh
cmake --install cpp/build --prefix /desired/prefix
```

## Use from another CMake project

For a checkout beside this repository, add the module directly and link its
public target:

```cmake
add_subdirectory("../ewb-grpc/cpp" ewb-grpc-cpp)
target_link_libraries(your-target PRIVATE zepben::protobuf)
```

After installing, locate it through CMake instead:

```cmake
find_package(zepben-protobuf CONFIG REQUIRED)
target_link_libraries(your-target PRIVATE zepben::protobuf)
```
