
bin_path=$(shell pwd)/bin
boot_path=${bin_path}/boot

mkdirs:
	mkdir -p ${bin_path}


install-boot: mkdirs
	cd ${bin_path}
	curl -fsSLo ${boot_path} https://github.com/boot-clj/boot-bin/releases/download/latest/boot.sh
	chmod 755 ${boot_path}

build: install-boot
	${boot_path} build-standalone
	cp -v target/*.jar ./


clean:
	rm -rv ${bin_path}
	rm -v *.jar
