
bin_path=$(shell pwd)/bin
boot_path=${bin_path}/boot

.PHONY: all mkdirs install-boot clean test


all: clean %.jar

mkdirs:
	mkdir -p ${bin_path}


install-boot: mkdirs
	cd ${bin_path}
	curl -fsSLo ${boot_path} https://github.com/boot-clj/boot-bin/releases/download/latest/boot.sh
	chmod 755 ${boot_path}

%.jar: install-boot
	${boot_path} build-standalone
	cp -v target/*.jar ./

test:
	${boot_path} run-test

clean:
	rm -frv ${bin_path}
	rm -fv *.jar

