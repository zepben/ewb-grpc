from setuptools import setup, find_namespace_packages

setup(
    name="zepben.protobuf",
    description="Evolve Python Protobuf and gRPC definitions",
    version="0.34.1b1",
    url="https://github.com/zepben/evolve-sdk-python",
    author="Kurt Greaves",
    author_email="kurt.greaves@zepben.com",
    license="Mozilla Public License v2.0",
    classifiers=[
        "License :: OSI Approved :: Mozilla Public License 2.0 (MPL 2.0)",
        "Programming Language :: Python :: 3",
        "Programming Language :: Python :: 3.7",
        "Operating System :: OS Independent"
    ],
    package_dir={"": "src"},
    packages=find_namespace_packages(where="src"),
    python_requires='>=3.9',
    setup_requires=[
        "grpcio-tools==1.59.3",
        "pep517"
    ],
    install_requires=[
        "protobuf==4.24.2",
        "grpcio==1.59.3",
    ],
)
