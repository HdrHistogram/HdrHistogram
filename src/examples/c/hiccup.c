#include <hdr_writer_reader_phaser.h>
#include <stdio.h>

int main(int argc, char** argv)
{
	struct hdr_writer_reader_phaser p;

	hdr_writer_reader_phaser_init(&p);

	for (int i = 0; i < 5; i++)
	{
		printf("%ld\n", hdr_phaser_writer_enter(&p));
	}

	return 0;
}