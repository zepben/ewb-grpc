default: c
file = EnergyMeter.proto

c: opendss/$(file)
	mv $< opendss/t.proto
	sed '/^package zepben/d' opendss/t.proto > $<
	cd opendss && protoc --c_out=. $(file) 
	mv opendss/t.proto $< 
