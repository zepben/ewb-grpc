# We only buold the EnergyMeter.proto file as that is all that is used by the hosting capacity
# C library, and we are not interested in supporting C at this stage.
default: c
proto_path = ../proto/zepben/protobuf
file = hc/opendss/EnergyMeter.proto

c: $(proto_path)/$(file)
	@mkdir -p out
	protoc -I $(proto_path) --c_out=out $(file)
