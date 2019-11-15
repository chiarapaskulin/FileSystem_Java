import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.FilterInputStream;

public class FileSystem {
	static int block_size = 1024; //1024 bytes
	static int blocks = 2048; //2048 blocos de 2024 bytes
	static int fat_size = blocks * 2; //4096 bytes
	static int fat_blocks = fat_size / block_size; //4 blocos
	static int root_block = fat_blocks; //4 blocos
	static int dir_entry_size = 32; //32 bytes
	static int dir_entries = block_size / dir_entry_size; //32 entradas
	/*
	0x0000 -> cluster livre
	0x0001 - 0x7ffd -> arquivo (ponteiro p/ proximo bloco)
	0x7ffe -> FAT
	0x7fff -> Fim de arquivo
	*/

	/* FAT data structure */
	final static short[] fat = new short[blocks]; //2048 representacoes de bloco de 2 bytes cada = 4096 bytes = 4 blocos
	/* data block */
	final static byte[] data_block = new byte[block_size]; //1 bloco local de tamanho 1024 bytes

	/* reads a data block from disk */
	public static byte[] readBlock(String file, int block) {
		byte[] record = new byte[block_size];
		try {
			RandomAccessFile fileStore = new RandomAccessFile(file, "rw");
			fileStore.seek(block * block_size);
			fileStore.read(record, 0, block_size);
			fileStore.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return record;
	}

	/* writes a data block to disk */
	public static void writeBlock(String file, int block, byte[] record) {
		try {
			RandomAccessFile fileStore = new RandomAccessFile(file, "rw");
			fileStore.seek(block * block_size);
			fileStore.write(record, 0, block_size);
			fileStore.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/* reads the FAT from disk */
	public static short[] readFat(String file) {
		short[] record = new short[blocks];
		try {
			RandomAccessFile fileStore = new RandomAccessFile(file, "rw");
			fileStore.seek(0);
			for (int i = 0; i < blocks; i++) {
				record[i] = fileStore.readShort();
			}
			fileStore.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return record;
	}

	/* writes the FAT to disk */
	public static void writeFat(String file, short[] fat) {
		try {
			RandomAccessFile fileStore = new RandomAccessFile(file, "rw");
			fileStore.seek(0);
			for (int i = 0; i < blocks; i++) {
				fileStore.writeShort(fat[i]);
			}
			fileStore.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/* reads a directory entry from a directory */
	public static DirEntry readDirEntry(int block, int entry) {
		byte[] bytes = readBlock("filesystem.dat", block);
		ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
		DataInputStream in = new DataInputStream(bis);
		DirEntry dir_entry = new DirEntry();

		try {
			in.skipBytes(entry * dir_entry_size);

			//nome do arquivo tem 25 bytes
			for (int i = 0; i < 25; i++){
				dir_entry.filename[i] = in.readByte();
			}
			dir_entry.attributes = in.readByte();
			dir_entry.first_block = in.readShort();
			dir_entry.size = in.readInt();

		} catch (IOException e) {
			e.printStackTrace();
		}

		return dir_entry;
	}

	/* writes a directory entry in a directory */
	public static void writeDirEntry(int block, int entry, DirEntry dir_entry) {
		byte[] bytes = readBlock("filesystem.dat", block);
		ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
		DataInputStream in = new DataInputStream(bis);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bos);

		try {
			for (int i = 0; i < entry * dir_entry_size; i++) {
				out.writeByte(in.readByte());
			}

			for (int i = 0; i < dir_entry_size; i++) {
				in.readByte();
			}

			for (int i = 0; i < 25; i++) {
				out.writeByte(dir_entry.filename[i]);
			}
			out.writeByte(dir_entry.attributes);
			out.writeShort(dir_entry.first_block);
			out.writeInt(dir_entry.size);

			for (int i = entry + 1; i < entry * dir_entry_size; i++) {
				out.writeByte(in.readByte());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		byte[] bytes2 = bos.toByteArray();
		for (int i = 0; i < bytes2.length; i++) {
			data_block[i] = bytes2[i];
		}
		writeBlock("filesystem.dat", block, data_block);
	}

	//init - inicializar o sistema de arquivos com as estruturas de dados, semelhante a formatar o sistema de arquivos virtual
	public static void init(){
		/* inicializa a FAT com as 4 (indices 0,1,2,3) primeiras entradas 0x7ffe para a própria FAT */
		for (int i = 0; i < fat_blocks; i++) {
			fat[i] = 0x7ffe;
		}

		/* inicializa a 5ª (indice 4) entrada da FAT com 0x7fff para indicar que é o ROOT */
		fat[root_block] = 0x7fff;

		/* inicializa todos outros blocos da FAT com 0 - do 6º (indice 4) ao 2048º (indice 2047) */
		for (int i = root_block + 1; i < blocks; i++) {
			fat[i] = 0;
		}

		/* escreve a FAT no disco - nos 4 primeiros blocos (indices 0,1,2,3) */
		writeFat("filesystem.dat", fat);

		/* escreve um bloco LOCAL zerado */
		for (int i = 0; i < block_size/*1024 bytes*/; i++) {
			data_block[i] = 0;
		}

		/* coloca esse bloco VAZIO na localização do ROOT - 5º bloco (indice 4) - no disco, ou seja, escreve o root vazio no disco*/
		writeBlock("filesystem.dat", root_block/*4*/, data_block);

		/* escreve todos outros blocos vazios - do 6º (indice 5) ao 2048º (indice 2047) */
		for (int i = root_block + 1; i < blocks; i++) {
			writeBlock("filesystem.dat", i, data_block);
		}
	}

	//ls [/caminho/diretorio] - listar diretorio
	public static void ls(String s){
		String[] arrOfStr = s.split("/");
		for (String a : arrOfStr) {
			System.out.println(a);
		}
	}

	//mkdir [/caminho/diretorio] - criar diretorio
	public static void mkdir(String s){
		String[] arrOfStr = s.split("/");

		//DirEntry dir = readDirEntry(4, 0);

		String[] newPath = new String[arrOfStr.length-1];

		int posicao = 0;
		for(int i=1; i<arrOfStr.length; i++){
			newPath[posicao] = arrOfStr[i];
		}

		follow(newPath,(short) 4);
	}

	private static void follow(String[] path, short blocoAtual){
		//se é o ultimo diretorio do path, acessa o mesmo
		if(path.length==1) access(path.toString(), blocoAtual);

		boolean found = false;

		//nome do diretorio que eu estou procurando
		byte[] file = path[0].getBytes();

		//confere cada entrada de diretório do blocoAtual
		for(int i=0; i<32 && found == false; i++){
			DirEntry entry = readDirEntry(blocoAtual, i);

			//compara o nome da entrada de diretorio atual com o nome do diretorio que eu estou procurando
			if(entry.filename == file){
				//se achou a entrada de diretorio, entra nela e passa path sem ela
				found = true;

				String[] newPath = new String[path.length-1];
				int posicao = 0;
				for(int k=1; k<path.length; k++){
					newPath[posicao] = path[i];
				}

				follow(newPath, entry.first_block);
			}
		}

		if(found == false) System.out.println("Não há nenhum diretório chamado /" +  path[0]);
	}

	private static void access(String s, short blocoAtual){
		boolean found = false;

		//nome do diretorio que eu estou procurando
		byte[] file = s.getBytes();

		//confere cada entrada de diretório do blocoAtual
		for(int i=0; i<32 && found==false; i++){
			DirEntry entry = readDirEntry(blocoAtual, i);

			//compara o nome da entrada de diretorio atual com o nome do diretorio que eu estou procurando
			if(entry.filename == file){
				//se achou a entrada de diretorio, printa que já existe e aborta
				found = true;
				System.out.println("O diretório chamado " + s + " já existe");
			}
		}

		//se o diretorio não existe, cria ele
		if(found == false){
			//procura a primeira entrada de diretorio vazia
			int i = 0;
			for(i=0; i<32 && found==false; i++){
				DirEntry entry = readDirEntry(blocoAtual, i);

				//confere se a entrada de diretorio esta vazia
				if(entry.attributes == 0){
					//se a entrada de diretorio esta vazia, sai do loop
					found = true;
				}
			}

			if(found==false){
				System.out.println("O diretório está cheio");
			}else {
				DirEntry dir_entry = new DirEntry();
				String name = s;
				byte[] namebytes = name.getBytes();
				for (int j = 0; j < namebytes.length; j++) {
					dir_entry.filename[j] = namebytes[j];
				}

				short firstblock = first_free_FAT_entry();
				if(firstblock==0){
					System.out.println("A FAT está cheia");
				}else {
					dir_entry.attributes = 0x02;
					dir_entry.first_block = firstblock;
					dir_entry.size = 0;
					writeDirEntry(blocoAtual, i, dir_entry);

					for (int j = 0; j < block_size/*1024 bytes*/; j++) {
						data_block[j] = 0;
					}

					writeBlock("filesystem.dat", firstblock, data_block);
				}
			}
		}
	}

	private static short first_free_FAT_entry(){
		//int i=0;
		for(int i=5; i<fat.length; i++){
			if(fat[i] == 0) return (short) i;
		}

		//0 deve ser tratado na chamada do método, pois indica que não há lugar na FAT
		return 0;
	}

	//create [/caminho/arquivo] - criar arquivo
	public static void createArchive(String s){
		String[] arrOfStr = s.split("/");
		for (String a : arrOfStr) {
			System.out.println(a);
		}
	}

	//unlink [/caminho/arquivo] - excluir arquivo ou diretorio (o diretorio precisa estar vazio)
	public static void unlink(String s){
		String[] arrOfStr = s.split("/");
		for (String a : arrOfStr) {
			System.out.println(a);
		}
	}

	//retorna se o Diretorio esta vazio
	public static boolean isDirEmpty(String s){
		return true;
	}

	//write "string" [/caminho/arquivo] - escrever dados em um arquivo (sobrescrever dados)
	public static void write(String s){
		String[] arrOfStr = s.split("/");
		for (String a : arrOfStr) {
			System.out.println(a);
		}
	}

	//append "string" [/caminho/arquivo] - anexar dados em um arquivo
	public static void append(String s){
		String[] arrOfStr = s.split("/");
		for (String a : arrOfStr) {
			System.out.println(a);
		}
	}

	//read [/caminho/arquivo] - ler o conteudo de um arquivo
	public static void read(String s){
		String[] arrOfStr = s.split("/");
		for (String a : arrOfStr) {
			System.out.println(a);
		}
	}

	public static void main(String args[]) {
		readFat("filesystem.dat");

		DirEntry dir_entry = new DirEntry();
		String name = "file1";
		byte[] namebytes = name.getBytes();
		for (int i = 0; i < namebytes.length; i++) {
			dir_entry.filename[i] = namebytes[i];
		}
		dir_entry.attributes = 0x01;
		dir_entry.first_block = 1111;
		dir_entry.size = 222;
		writeDirEntry(root_block, 0, dir_entry);


		/* fill three root directory entries and list them */
		name = "file2";
		namebytes = name.getBytes();
		for (int i = 0; i < namebytes.length; i++) {
			dir_entry.filename[i] = namebytes[i];
		}
		dir_entry.attributes = 0x01;
		dir_entry.first_block = 2222;
		dir_entry.size = 333;
		writeDirEntry(root_block, 1, dir_entry);

		name = "file3";
		namebytes = name.getBytes();
		for (int i = 0; i < namebytes.length; i++) {
			dir_entry.filename[i] = namebytes[i];
		}
		dir_entry.attributes = 0x01;
		dir_entry.first_block = 3333;
		dir_entry.size = 444;
		writeDirEntry(root_block, 2, dir_entry);

		/* list entries from the root directory */
		//for (int i = 0; i < dir_entries; i++) {
		//	dir_entry = readDirEntry(root_block, i);
		//	System.out.println("Entry " + i + ", file: " + new String(dir_entry.filename) + " attr: " +
		//	dir_entry.attributes + " first: " + dir_entry.first_block + " size: " + dir_entry.size);
		//}
	}
}

