# We only build the files used by the hosting capacity C library, as we are not interested in supporting C at this stage.
PROTO_PATH = ../proto/zepben/protobuf
HC_PATH = hc/opendss
FILES = Diagnostics \
        EnergyMeter \
        OpenDssReport

all:
	@mkdir -p out
	protoc -I $(PROTO_PATH) --c_out=out $(foreach FILE,$(FILES),$(PROTO_PATH)/$(HC_PATH)/$(FILE).proto)
