from setuptools import setup, find_namespace_packages

test_deps = ["pytest", "pytest-asyncio"]
setup(
    name="zepben.protobuf",
    description="Evolve Python SDK",
    version="0.3.2b0",
    package_dir={"": "src"},
    packages=find_namespace_packages(where="src"),
    setup_requires=[
        "grpcio-tools",
        "pep517"
    ],
    install_requires=[
        "protobuf",
        "grpcio",
        "grpcio-tools",
    ],
    extras_require={
        "test": test_deps,
    }
)
