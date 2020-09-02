"""
Copyright 2020 Zeppelin Bend Pty Ltd
This file is part of evolve-grpc.

evolve-grpc is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

evolve-grpc is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with evolve-grpc.  If not, see <https://www.gnu.org/licenses/>.
"""
import argparse
from typing import List

import grpc_tools.protoc
import pkg_resources
import glob
import os
import sys
import shutil
import logging
from pathlib import Path
from pep517.envbuild import build_sdist, build_wheel

logging.basicConfig(format="%(asctime)s - %(name)s:%(lineno)d - %(levelname)s - %(message)s")
logger = logging.getLogger("build.py")
logger.setLevel(logging.INFO)

DIST_DIR = Path(os.path.curdir, "dist")


def clean_generated_files(output_dir):
    files = glob.glob(f"{output_dir}/**/*pb2*.py", recursive=True)
    for file in files:
        os.remove(file)
    return 0


def create_init_files(root):
    for directory, _, _ in os.walk(root):
        if directory == root:
            continue
        logger.debug(f"Creating {directory}/__init__.py")
        Path(f"{directory}/__init__.py").touch()


def clean(output_dir, full=False):
    if full:
        try:
            shutil.rmtree(output_dir)
            # Cleanup setuptools dist dir
            shutil.rmtree("dist")
            return 0
        except FileNotFoundError as fnfe:  # Directory didn't exist
            logger.info(f"Output directory '{fnfe.filename}' didn't exist. Clean successful")
            return 0

    return clean_generated_files(output_dir)


def build_protos(args):
    os.makedirs(os.path.join(args.output_dir, args.namespace), exist_ok=True)
    files = glob.glob(f"{args.protos}", recursive=True)
    if args.include:
        files_filtered = {f for f in files if f in map(lambda p: p.replace("/", os.sep), args.include)}
    else:
        files_filtered = files

    proto_include = pkg_resources.resource_filename('grpc_tools', '_proto')
    logger.info(f"Compiling protos: {', '.join(files_filtered)}")

    result = grpc_tools.protoc.main([
        'grpc_tools.protoc',
        f'-I{os.path.join("..", "proto")}',
        f'-I{proto_include}',
        f'--python_out={args.output_dir}/',
        f'--grpc_python_out={args.output_dir}/',
        *files_filtered
    ])
    if result != 0:
        return result
    create_init_files(os.path.join(args.output_dir, args.namespace))
    return result


def package():
    """
    Package each project. If projects is set
    :param projects: The list of projects to package. If None, all projects will be packaged.
    :return: 0 if packaging was successful, -1 if failed.
    """
    try:
        # Clean any existing build dir first, otherwise may accidentally group packages.
        shutil.rmtree("build", ignore_errors=True)

        build_wheel(os.path.curdir, str(DIST_DIR.absolute()))

        return 0
    except FileNotFoundError as fnfe:
        return -1


def main():
    parser = argparse.ArgumentParser(description="evolve-grpc build script")
    parser.add_argument('--clean', help='Clean existing generated code', action='store_true', default=False)
    parser.add_argument('--fullclean', help='Removes the output directory in its entirety.', action='store_true', default=False)
    parser.add_argument('--protos', help="Path to proto files.", default=os.path.join("..", "proto", "zepben", "**", "*.proto"))
    parser.add_argument('--output-dir', help="Output directory for generated code", default="src")
    parser.add_argument('--namespace', help="Namespace to use for generated code", default=os.path.join("zepben", "protobuf"))
    parser.add_argument('--package', help="Package the projects", action='store_true')
    parser.add_argument('include', help="Proto files to build. Defaults to everything found in --protos.",
                        nargs=argparse.REMAINDER)
    args = parser.parse_args()
    if args.clean or args.fullclean:
        return clean(args.output_dir, args.fullclean)

    ret = build_protos(args)
    if ret != 0:
        return ret

    if args.package:
        package()


if __name__ == "__main__":
    sys.exit(main())
