public class DirEntry {
	byte[] filename = new byte[25];

	//0x00 - em branco
	//0x01 - arquivo
	//0x02 - diretorio
	byte attributes;

	short first_block;
	int size;
}