# We only build the files used by the hosting capacity C library, as we are not interested in supporting C at this stage.
PROTO_PATH = ../proto
HC_PATH = zepben/protobuf/hc/opendss
FILES = Diagnostics \
        EnergyMeter \
        OpenDssReport

DOCKER_BIN := $(shell { command -v podman || command -v docker ; } 2>/dev/null)
TAG := $(shell { packer inspect builder-container/alpine-protoc.pkr.hcl | grep var.tag | cut -f2 -d' ' | sed -e "s/\"//g";} 2> /dev/null )

all:
	@mkdir -p out
	protoc -I $(PROTO_PATH) --c_out=out $(foreach FILE,$(FILES),$(PROTO_PATH)/$(HC_PATH)/$(FILE).proto)

container-lib:
	$(DOCKER_BIN) run -v $(realpath ..):/app zepben/grpc-c-builder:$(TAG) ash -c "cd /app/c && make"
