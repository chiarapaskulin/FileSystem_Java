import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Scanner;

public class FileSystem {
    private static final int BLOCK_SIZE = 1024; //1024 bytes
    private static final int BLOCKS = 2048; //2048 blocos de 2024 bytes
    private static final int FAT_SIZE = BLOCKS * 2; //4096 bytes
    private static final int FAT_BLOCKS = FAT_SIZE / BLOCK_SIZE; //4 blocos
    private static final int ROOT_BLOCK = FAT_BLOCKS; //4 blocos
    private static final int DIR_ENTRY_SIZE = 32; //32 bytes
    static final int dir_entries = BLOCK_SIZE / DIR_ENTRY_SIZE; //32 entradas

    private static final int FAT = 0x7ffe;
    private static final int FIM_DE_ARQUIVO = 0x7fff;
    private static final int TIPO_ARQUIVO = 0x01;

	/*
	0x0000 -> cluster livre
	0x0001 - 0x7ffd -> arquivo (ponteiro p/ proximo bloco)
	*/

    /* FAT data structure */
    private static short[] fat = new short[BLOCKS]; //2048 representacoes de bloco de 2 bytes cada = 4096 bytes = 4 blocos
    /* data block */
    private static byte[] data_block = new byte[BLOCK_SIZE]; //1 bloco local de tamanho 1024 bytes


    //------------------------METODOS DE MANIPULACAO DE MEMORIA--------------------------------

    /* reads a data block from disk */
    private static byte[] readBlock(int block) {
        byte[] record = new byte[BLOCK_SIZE];
        try {
            RandomAccessFile fileStore = new RandomAccessFile("../filesystem.dat", "rw");
            fileStore.seek(block * BLOCK_SIZE);
            fileStore.read(record, 0, BLOCK_SIZE);
            fileStore.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return record;
    }

    /* writes a data block to disk */
    private static void writeBlock(int block, byte[] record) {
        try {
            RandomAccessFile fileStore = new RandomAccessFile("../filesystem.dat", "rw");
            fileStore.seek(block * BLOCK_SIZE);
            fileStore.write(record, 0, BLOCK_SIZE);
            fileStore.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* reads the FAT from disk */
    private static short[] readFat() {
        short[] record = new short[BLOCKS];
        try {
            RandomAccessFile fileStore = new RandomAccessFile("../filesystem.dat", "rw");
            fileStore.seek(0);
            for (int i = 0; i < BLOCKS; i++) {
                record[i] = fileStore.readShort();
            }
            fileStore.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return record;
    }

    /* writes the FAT to disk */
    private static void writeFat(short[] fat) {
        try {
            RandomAccessFile fileStore = new RandomAccessFile("../filesystem.dat", "rw");
            fileStore.seek(0);
            for (int i = 0; i < BLOCKS; i++) {
                fileStore.writeShort(fat[i]);
            }
            fileStore.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* reads a directory entry from a directory */
    private static DirEntry readDirEntry(int block, int entry) {
        byte[] bytes = readBlock(block);
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        DataInputStream in = new DataInputStream(bis);
        DirEntry dir_entry = new DirEntry();

        try {
            in.skipBytes(entry * DIR_ENTRY_SIZE);

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
    private static void writeDirEntry(int block, int entry, DirEntry dir_entry) {
        byte[] bytes = readBlock(block);
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        DataInputStream in = new DataInputStream(bis);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);

        try {
            for (int i = 0; i < entry * DIR_ENTRY_SIZE; i++) {
                out.writeByte(in.readByte());
            }

            for (int i = 0; i < DIR_ENTRY_SIZE; i++) {
                in.readByte();
            }

            for (int i = 0; i < 25; i++) {
                out.writeByte(dir_entry.filename[i]);
            }
            out.writeByte(dir_entry.attributes);
            out.writeShort(dir_entry.first_block);
            out.writeInt(dir_entry.size);

            for (int i = entry + 1; i < entry * DIR_ENTRY_SIZE; i++) {
                out.writeByte(in.readByte());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        byte[] bytes2 = bos.toByteArray();
        System.arraycopy(bytes2, 0, data_block, 0, bytes2.length);
        writeBlock(block, data_block);
    }


    //------------------------METODO DE INIT--------------------------------

    //init - inicializar o sistema de arquivos com as estruturas de dados, semelhante a formatar o sistema de arquivos virtual
    private static void init(){
        /* inicializa a FAT com as 4 (indices 0,1,2,3) primeiras entradas 0x7ffe para a própria FAT */
        for (int i = 0; i < FAT_BLOCKS; i++) {
            fat[i] = FAT;
        }

        /* inicializa a 5ª (indice 4) entrada da FAT com 0x7fff para indicar que é o ROOT */
        fat[ROOT_BLOCK] = FIM_DE_ARQUIVO;

        /* inicializa todos outros blocos da FAT com 0 - do 6º (indice 4) ao 2048º (indice 2047) */
        for (int i = ROOT_BLOCK + 1; i < BLOCKS; i++) {
            fat[i] = 0;
        }

        /* escreve a FAT no disco - nos 4 primeiros blocos (indices 0,1,2,3) */
        writeFat(fat);

        /* escreve um bloco LOCAL zerado */
        for (int i = 0; i < BLOCK_SIZE/*1024 bytes*/; i++) {
            data_block[i] = 0;
        }

        /* coloca esse bloco VAZIO na localização do ROOT - 5º bloco (indice 4) - no disco, ou seja, escreve o root vazio no disco*/
        writeBlock(ROOT_BLOCK/*4*/, data_block);

        /* escreve todos outros blocos vazios - do 6º (indice 5) ao 2048º (indice 2047) */
        for (int i = ROOT_BLOCK + 1; i < BLOCKS; i++) {
            writeBlock(i, data_block);
        }
    }


    //------------------------METODOS GERAIS--------------------------------

    //devolve a primeira entrada vazia (com valor 0) da FAT e -1 se estiver cheia
    private static short firstFreeFATEntry(){
        //i começa em 5 pois de 0 a 3 são os blocos da FAT e 4 é o bloco do root
        for(int i=5; i<fat.length; i++){
            if(fat[i] == 0) return (short) i;
        }

        //-1 deve ser tratado na chamada do método, pois indica que não há lugar na FAT
        return -1;
    }

    //devolve a primeira entrada vazia (com valor 0) do diretorioe -1 se estiver cheio
    private static short firstFreeDirEntry(int blocoAtual){
        for(int i=0; i<32; i++){
            DirEntry entry = readDirEntry(blocoAtual, i);

            //confere se a entrada de diretorio esta vazia
            if(entry.attributes == 0){
                //se a entrada de diretorio esta vazia, retorna seu numero
                return (short) i;
            }
        }

        //-1 deve ser tratado na chamada do método, pois indica que não há lugar no bloco
        return -1;
    }

    //confere se uma entrada existe no blocoAtual passado por parametro
    private static boolean doesEntryExists(int blocoAtual, String path){
        //confere cada entrada de diretório do blocoAtual
        for(int i = 0; i < 32; i++){
            DirEntry entry = readDirEntry(blocoAtual, i);
            if(entry.attributes != 0) {
                String dirName = getDirName(entry);

                //compara o nome da entrada de diretorio atual com o nome do diretorio que eu estou procurando
                if (dirName.equals(path)) {
                    //se achou a entrada de diretorio, retorna true
                    return true;
                }
            }
        }

        return false;
    }

    //arredonda o double passado por parametro para cima
    private static int roundUp(double num) {
        if((num-(int)num) > 0.0 ) {
            num += 1;
        }
        return (int)num;
    }

    //monta uma string com o nome do diretorio
    private static String getDirName(DirEntry entry) {
        StringBuilder dirName = new StringBuilder();
        byte[] b = new byte[1];
        for(int k = 0; k < entry.filename.length; k++){
            if(entry.filename[k]!=0){
                b[0] = entry.filename[k];
                try {
                    dirName.append(new String(b, StandardCharsets.UTF_8));
                } catch(Exception ignored){}
            }
            else break;
        }
        return dirName.toString();
    }


    //------------------------METODOS DO LS--------------------------------

    //ls [/caminho/diretorio] - listar diretorio
    private static void ls(String path){
        String[] arrOfStr = path.split("/");
        //passa o path completo
        //a primeira posicao do array é o diretorio ATUAL
        //o blocoAtual é o número do bloco do diretorio ATUAL
        followUntilFindDir(arrOfStr,(short) 4);
    }

    //vai acessando os subdiretorios até o ultimo e chama accessAndListDir para listar o ultimo diretorio
    private static void followUntilFindDir(String[] path, short blocoAtual){
        //se é o ultimo diretorio do path, acessa seu diretorio pai, acessa ele e lista ele
        if(path.length <= 1) {
            accessAndListDir(path[0], blocoAtual);
        }
        else{
            boolean found = false;

            //nome do diretorio que eu estou procurando é pego no path[1], porque path[0] é o atual
            String arcName = path[1];

            //confere cada entrada de diretório do blocoAtual
            for (int i = 0; i < 32 && !found; i++){
                DirEntry entry = readDirEntry(blocoAtual, i);
                String dirName = getDirName(entry);

                //compara o nome da entrada de diretorio atual com o nome do diretorio que eu estou procurando
                if (dirName.equals(arcName)) {
                    //se achou a entrada de diretorio, entra nela e passa path sem o diretorio atual
                    found = true;

                    String[] newPath = new String[path.length - 1];
                    int posicao = 0;
                    for (int k = 1; k < path.length; k++) {
                        newPath[posicao] = path[k];
                        posicao++;
                    }

                    //chama o metodo recursivamente com path[0], que agora é o diretorio que vamos entrar
                    //e com entry.first_bloc, que é o numero do bloco desse diretorio
                    followUntilFindDir(newPath, entry.first_block);
                }
            }

            //printa que não achou o diretorio path[1], que é o que está sendo procurado no atual path[0]
            if (!found) System.out.println("Não há nenhum diretório chamado /" + path[1]);
        }
    }

    //lista o diretorio descrito em path que tem seu bloco como blocoAtual
    private static void accessAndListDir(String path, short blocoAtual){
        //nome do diretorio atual de blocoAtual
        byte[] file = path.getBytes();

        DirEntry dir_entry;

        //printa cada entrada de diretório do blocoAtual que não seja vazia
        for(int i=0; i<32; i++){
            dir_entry = readDirEntry(blocoAtual,i);
            //se a entrada de diretório nao esta vazia, printa seu nome
            if (dir_entry.attributes != 0) {
                String s = "";
                try {
                    s = new String(dir_entry.filename, StandardCharsets.UTF_8);
                } catch (Exception ignored){};
                System.out.println(s);
            }
        }
    }


    //------------------------METODOS DO MKDIR--------------------------------

    //mkdir [/caminho/diretorio] - criar diretorio
    private static void mkdir(String path) {
        String[] arrOfStr = path.split("/");

        String[] newPath = new String[arrOfStr.length-1];

        //passa o path a partir do diretorio seguinte
        int posicao = 0;
        for(int i=1; i<arrOfStr.length; i++){
            newPath[posicao] = arrOfStr[i];
            posicao++;
        }

        //a primeira posicao do array é o diretorio SEGUINTE, o que tem que ser buscado no blocoAtual
        //o blocoAtual é o número do bloco do diretorio ATUAL
        followUntilCreateDir(newPath, (short) 4);
    }

    //vai acessando os subdiretorios até o ultimo e chama accessAndCreateDir para criar o ultimo
    private static void followUntilCreateDir(String[] path, short blocoAtual){
        //se é o ultimo diretorio do path, o que tem que ser criado, acessa o blocoAtual (diretorio do que tem que ser criado) e cria o mesmo
        if(path.length <= 1) {
            accessAndCreateDir(path[0], blocoAtual);
        } else {
            boolean found = false;

            //nome do diretorio que eu estou procurando no blocoAtual
            String arcName = path[0];

            //confere cada entrada de diretório do blocoAtual para ver se acha nele o diretorio que eu estou procurando
            for (int i = 0; i < 32 && !found; i++) {
                DirEntry entry = readDirEntry(blocoAtual, i);
                String dirName = getDirName(entry);

                //compara o nome da entrada de diretorio atual com o nome do diretorio que eu estou procurando
                if (dirName.equals(arcName)) {
                    //se achou a entrada de diretorio, entra nela e passa path sem ela
                    found = true;

                    String[] newPath = new String[path.length - 1];
                    int posicao = 0;
                    for (int k = 1; k < path.length; k++) {
                        newPath[posicao] = path[k];
                        posicao++;
                    }

                    followUntilCreateDir(newPath, entry.first_block);
                }
            }

            if (!found) System.out.println("Não há nenhum diretório chamado /" + path[0]);
        }
    }

    //cria o diretorio descrito em path como entrada de diretorio no blocoAtual e como diretorio na FAT
    private static void accessAndCreateDir(String path, short blocoAtual){
        boolean found = false;

        //se o diretorio existe, printa que ele já existe
        if(doesEntryExists(blocoAtual, path)){
            System.out.println("O arquivo/entrada de diretório chamado ''" + path + "'' já existe");

            //se o diretorio não existe, cria ele
        } else {
            //procura a primeira entrada de diretorio vazia para criar o subdiretorio
            int entradaDeDirVazia = firstFreeDirEntry(blocoAtual);

            //se não achou uma entrada de diretorio vazia, avisa que ele esta cheio
            if(entradaDeDirVazia == -1) {
                System.out.println("O diretório está cheio");

                //se achou uma entrada de diretorio vaiza, prossegue
            } else {
                //procura a primeira entrada livre da FAT
                short firstblock = firstFreeFATEntry();

                //return -1 significa que a FAT está cheia, então para de processar
                if(firstblock == -1) {
                    System.out.println("A FAT está cheia");

                } else {
                    //define a entrada firstblock da FAT como utilizada (fim de arquivo 0x7fff)
                    fat[firstblock] = FIM_DE_ARQUIVO;
                    //atualiza a FAT no arquivo .dat
                    writeFat(fat);

                    //cria a entrada de diretorio para adicionar na entrada de diretorio vazia do blocoAtual
                    DirEntry dir_entry = new DirEntry();
                    byte[] namebytes = path.getBytes();
                    System.arraycopy(namebytes, 0, dir_entry.filename, 0, namebytes.length);

                    //define informacoes da entrada de diretorio
                    dir_entry.attributes = 0x02;
                    dir_entry.first_block = firstblock;
                    dir_entry.size = 0;
                    //escreve a entrada de diretorio criada na entrada de diretorio i do blocoAtual
                    writeDirEntry(blocoAtual, entradaDeDirVazia, dir_entry);

                    //cria um bloco completamente VAZIO
                    for (int j = 0; j < BLOCK_SIZE/*1024 bytes*/; j++) {
                        data_block[j] = 0;
                    }

                    //escreve o bloco VAZIO criado no arquivo .dat
                    writeBlock(firstblock, data_block);
                    writeFat(fat);
                }
            }
        }
    }


    //------------------------METODOS DO CREATEARCHIVE--------------------------------

    //create [/caminho/arquivo] - criar arquivo
    private static void createArchive(String path, String content, int size){
        String[] arrOfStr = path.split("/");
        //passa o path completo
        //a primeira posicao do array é o diretorio ATUAL
        //o blocoAtual é o número do bloco do diretorio ATUAL
        followUntilCreateArchive(arrOfStr,(short) 4, content, size);
    }

    //vai acessando os subdiretorios até o penultimo e chama accessAndCreateArchive para criar o ultimo dentro do penultimo
    private static void followUntilCreateArchive(String[] path, short blocoAtual, String content, int size){
        //se [0] é o ultimo diretorio do path e [1] é o arquivo que tem que ser criado, acessa ele e cria o arquivo
        if(path.length <= 2) {
            accessAndCreateArchive(path, blocoAtual, content, size);
        } else {
            boolean found = false;

            //nome do diretorio que eu estou procurando é pego no path[1], porque path[0] é o atual
            String arcName = path[1];

            //confere cada entrada de diretório do blocoAtual
            for (int i = 0; i < 32 && !found; i++) {
                DirEntry entry = readDirEntry(blocoAtual, i);
                String dirName = getDirName(entry);

                //compara o nome da entrada de diretorio atual com o nome do diretorio que eu estou procurando
                if(dirName.equals(arcName)) {
                    //se achou a entrada de diretorio, entra nela e passa path sem o diretorio atual
                    found = true;

                    String[] newPath = new String[path.length - 1];
                    int posicao = 0;
                    for (int k = 1; k < path.length; k++) {
                        newPath[posicao] = path[k];
                        posicao++;
                    }

                    //chama o metodo recursivamente com path[0], que agora é o diretorio que vamos entrar
                    //e com entry.first_bloc, que é o numero do bloco desse diretorio
                    followUntilCreateArchive(newPath, entry.first_block, content, size);
                }
            }

            //printa que não achou o diretorio path[1], que é o que está sendo procurado no atual path[0]
            if (!found) System.out.println("Não há nenhum diretório chamado /" + path[1]);
        }
    }

    //cria o diretorio descrito em path[1] dentro de path[0]
    private static void accessAndCreateArchive(String[] path, short blocoAtual, String content, int size) {
        if(doesEntryExists(blocoAtual,path[1])) {
            System.out.println("O arquivo/entrada de diretório chamado ''" + path[1] + "'' já existe");
            //se não tem nenhuma entrada de diretório com esse nome, cria o arquivo
        } else {
            //procura a primeira entrada de diretorio vazia para criar o arquivo
            int entradaDeDirVazia = firstFreeDirEntry(blocoAtual);

            //se não achou uma entrada de diretorio vazia, avisa que ele esta cheio
            if(entradaDeDirVazia == -1){
                System.out.println("O diretório está cheio");

                //se achou uma entrada de diretorio vazia, prossegue com a criacao do arquivo
            } else {
                //procura a primeira entrada livre da FAT
                short firstblock = firstFreeFATEntry();
                //return 0 significa que a FAT está cheia, então para de processar
                if(firstblock == -1) {
                    System.out.println("A FAT está cheia");
                } else {
                    //faz um processamento especial para um arquivo maior que 1024 bytes
                    if(size > 1024) {
                        //define a quantidade de blocos que terão que ser utilizados
                        int qt_blocos = roundUp(size/1024);

                        //cria um Array de números de blocos para facilitar a manipulacao
                        ArrayList<Short> blocosFAT = new ArrayList<>();

                        //adiciona 0x7fff no primeiro bloco (para indicar que ele está em uso), adiciona ele na lista e subtrai a quantidade de blocos necessaria
                        blocosFAT.add(firstblock);
                        fat[firstblock] = FIM_DE_ARQUIVO;
                        qt_blocos--;

                        //procura o proximo bloco vazio, adiciona ele na lista e indica que ele está em uso; subtrai a qtidade de blcoos necessaria
                        for(int i = 0; i < qt_blocos; i++){
                            short nextblock = firstFreeFATEntry();
                            blocosFAT.add(nextblock);
                            fat[nextblock] = FIM_DE_ARQUIVO;
                            qt_blocos--;
                        }

                        //depois que todos os blocos estão marcados como 0x7fff (final de arquivo),
                        //marcamos de trás pra frente a ligação de um com os outros
                        //o ultimo bloco já está marcado como fim de arquivo, o que é correto (por isso começamos com blocosFAT.size()-2
                        //agora devemos marcar o penultimo bloco com o numero do ultimo e assim por diante
                        for(int i = blocosFAT.size() - 2; i >= 0; i++){
                            short b_atual = blocosFAT.get(i);
                            short b_anterior = blocosFAT.get(i+1);
                            fat[b_atual] = b_anterior;
                        }

                        //atualiza a FAT no arquivo .dat
                        writeFat(fat);

                        //cria a entrada de diretorio com o arquivo para adicionar na entrada de diretorio do blocoAtual
                        DirEntry dir_entry = new DirEntry();
                        String name = path[1];
                        byte[] namebytes = name.getBytes();
                        System.arraycopy(namebytes, 0, dir_entry.filename, 0, namebytes.length);

                        //define informacoes da entrada de diretorio
                        dir_entry.attributes = TIPO_ARQUIVO;
                        dir_entry.first_block = firstblock;
                        dir_entry.size = size;

                        //escreve a entrada de diretorio criada na entrada de diretorio i do blocoAtual
                        writeDirEntry(blocoAtual, entradaDeDirVazia, dir_entry);


                        //cria blocos com o conteúdo que foi passado por parâmetro
                        byte[] contentBytes = content.getBytes();
                        int contConteudo = 0;

                        //para cada bloco, escreve o conteudo dentro dele e adiciona no .dat
                        for(int i=0; i<blocosFAT.size(); i++){
                            //em cada bloco, coloca 1024 bytes de conteudo até acabar o conteudo, daí passa a colocar 0
                            for (int j = 0; j < 1024; j++) {
                                if(contConteudo>contentBytes.length){
                                    data_block[j] = 0;
                                }else {
                                    data_block[j] = contentBytes[contConteudo];
                                    contConteudo++;
                                }
                            }
                            //escreve o bloco criado com o conteúdo no arquivo .dat na posicao da FAT do bloco
                            writeBlock(blocosFAT.get(i), data_block);
                        }

                    } else {
                        //define a entrada firstblock da FAT como utilizada (fim de arquivo 0x7fff)
                        fat[firstblock] = FIM_DE_ARQUIVO;

                        //atualiza a FAT no arquivo .dat
                        writeFat(fat);

                        //cria a entrada de diretorio com o arquivo para adicionar na entrada de diretorio do blocoAtual
                        DirEntry dir_entry = new DirEntry();
                        String name = path[1];
                        byte[] namebytes = name.getBytes();
                        System.arraycopy(namebytes, 0, dir_entry.filename, 0, namebytes.length);

                        //define informacoes da entrada de diretorio
                        dir_entry.attributes = TIPO_ARQUIVO;
                        dir_entry.first_block = firstblock;
                        dir_entry.size = size;

                        //escreve a entrada de diretorio criada na entrada de diretorio i do blocoAtual
                        writeDirEntry(blocoAtual, entradaDeDirVazia, dir_entry);

                        //cria um bloco com o conteúdo que foi passado por parâmetro
                        byte[] contentBytes = content.getBytes();
                        /*menor que 1024 bytes*/
                        System.arraycopy(contentBytes, 0, data_block, 0, contentBytes.length);
                        //escreve o bloco criado com o conteúdo no arquivo .dat
                        writeBlock(firstblock, data_block);
                    }
                }
            }
        }
    }


    //------------------------METODOS DO WRITE--------------------------------

    //write "string" [/caminho/arquivo] - escrever dados em um arquivo (sobrescrever dados)
    public static void writeArchive(String path, String content, int size) {
        String[] arrOfStr = path.split("/");
        followUntilWriteArchive(arrOfStr,(short) 4, content, size);
    }

    //vai acessando os subdiretorios até o penultimo e chama accessAndCreateArchive para criar o ultimo dentro do penultimo
    private static void followUntilWriteArchive(String[] path, short blocoAtual, String content, int size) {
        //se [0] é o ultimo diretorio do path e [1] é o arquivo que tem que ser modificado, acessa ele e cria o arquivo
        if(path.length <= 2) {
            accessAndWriteArchive(path, content, size);
        } else {
            boolean found = false;

            //nome do diretorio que eu estou procurando é pego no path[1], porque path[0] é o atual
            String arcName = path[1];

            //confere cada entrada de diretório do blocoAtual
            for (int i = 0; i < 32 && !found; i++) {
                DirEntry entry = readDirEntry((short) 4, i);
                String dirName = getDirName(entry);

                //compara o nome da entrada de diretorio atual com o nome do diretorio que eu estou procurando
                if(dirName.equals(arcName)) {
                    //se achou a entrada de diretorio, entra nela e passa path sem o diretorio atual
                    found = true;

                    String[] newPath = new String[path.length - 1];
                    int posicao = 0;
                    for (int k = 1; k < path.length; k++) {
                        newPath[posicao] = path[k];
                        posicao++;
                    }

                    //chama o metodo recursivamente com path[0], que agora é o diretorio que vamos entrar
                    //e com entry.first_bloc, que é o numero do bloco desse diretorio
                    followUntilWriteArchive(newPath, entry.first_block, content, size);
                }
            }

            //printa que não achou o diretorio path[1], que é o que está sendo procurado no atual path[0]
            if (!found) System.out.println("Não há nenhum diretório chamado /" + path[1]);
        }
    }

    //cria o diretorio descrito em path[1] dentro de path[0]
    private static void accessAndWriteArchive(String[] path, String content, int size) {
        //procura a primeira entrada de diretorio vazia para criar o arquivo
        int entradaDeDirVazia = firstFreeDirEntry((short) 4);

        //se não achou uma entrada de diretorio vazia, avisa que ele esta cheio
        if(entradaDeDirVazia == -1) {
            System.out.println("O diretório está cheio");

            //se achou uma entrada de diretorio vazia, prossegue com a criacao do arquivo
        } else {
            //procura a primeira entrada livre da FAT
            short firstblock = firstFreeFATEntry();

            //return 0 significa que a FAT está cheia, então para de processar
            if(firstblock == -1) {
                System.out.println("A FAT está cheia");
            } else {
                //faz um processamento especial para um arquivo maior que 1024 bytes
                if(size > 1024) {
                    //define a quantidade de blocos que terão que ser utilizados
                    int qt_blocos = roundUp(size/1024);

                    //cria um Array de números de blocos para facilitar a manipulacao
                    ArrayList<Short> blocosFAT = new ArrayList<>();

                    //adiciona 0x7fff no primeiro bloco (para indicar que ele está em uso), adiciona ele na lista e subtrai a quantidade de blocos necessaria
                    blocosFAT.add(firstblock);
                    fat[firstblock] = FIM_DE_ARQUIVO;
                    qt_blocos--;

                    //procura o proximo bloco vazio, adiciona ele na lista e indica que ele está em uso; subtrai a qtidade de blcoos necessaria
                    for(int i = 0; i < qt_blocos; i++){
                        short nextblock = firstFreeFATEntry();
                        blocosFAT.add(nextblock);
                        fat[nextblock] = FIM_DE_ARQUIVO;
                        qt_blocos--;
                    }

                    //depois que todos os blocos estão marcados como 0x7fff (final de arquivo),
                    //marcamos de trás pra frente a ligação de um com os outros
                    //o ultimo bloco já está marcado como fim de arquivo, o que é correto (por isso começamos com blocosFAT.size()-2
                    //agora devemos marcar o penultimo bloco com o numero do ultimo e assim por diante
                    for(int i = blocosFAT.size() - 2; i >= 0; i++) {
                        short b_atual = blocosFAT.get(i);
                        short b_anterior = blocosFAT.get(i + 1);
                        fat[b_atual] = b_anterior;
                    }

                    //atualiza a FAT no arquivo .dat
                    writeFat(fat);

                    //cria a entrada de diretorio com o arquivo para adicionar na entrada de diretorio do blocoAtual
                    DirEntry dir_entry = new DirEntry();
                    String name = path[1];
                    byte[] namebytes = name.getBytes();
                    for (int j = 0; j < namebytes.length; j++) {
                        dir_entry.filename[j] = namebytes[j];
                    }

                    //define informacoes da entrada de diretorio
                    dir_entry.attributes = TIPO_ARQUIVO;
                    dir_entry.first_block = firstblock;
                    dir_entry.size = size;

                    //escreve a entrada de diretorio criada na entrada de diretorio i do blocoAtual
                    writeDirEntry((short) 4, entradaDeDirVazia, dir_entry);

                    //cria blocos com o conteúdo que foi passado por parâmetro
                    byte[] contentBytes = content.getBytes();
                    int contConteudo = 0;

                    //para cada bloco, escreve o conteudo dentro dele e adiciona no .dat
                    for(int i = 0; i < blocosFAT.size(); i++) {
                        //em cada bloco, coloca 1024 bytes de conteudo até acabar o conteudo, daí passa a colocar 0
                        for (int j = 0; j < 1024; j++) {
                            if(contConteudo > contentBytes.length){
                                data_block[j] = 0;
                            } else {
                                data_block[j] = contentBytes[contConteudo];
                                contConteudo++;
                            }
                        }
                        //escreve o bloco criado com o conteúdo no arquivo .dat na posicao da FAT do bloco
                        writeBlock(blocosFAT.get(i), data_block);
                    }

                } else {
                    //define a entrada firstblock da FAT como utilizada (fim de arquivo 0x7fff)
                    fat[firstblock] = FIM_DE_ARQUIVO;

                    //atualiza a FAT no arquivo .dat
                    writeFat(fat);

                    //cria a entrada de diretorio com o arquivo para adicionar na entrada de diretorio do blocoAtual
                    DirEntry dir_entry = new DirEntry();
                    String name = path[1];
                    byte[] namebytes = name.getBytes();
                    System.arraycopy(namebytes, 0, dir_entry.filename, 0, namebytes.length);

                    //define informacoes da entrada de diretorio
                    dir_entry.attributes = TIPO_ARQUIVO;
                    dir_entry.first_block = firstblock;
                    dir_entry.size = size;

                    //escreve a entrada de diretorio criada na entrada de diretorio i do blocoAtual
                    writeDirEntry((short) 4, entradaDeDirVazia, dir_entry);

                    //cria um bloco com o conteúdo que foi passado por parâmetro
                    byte[] contentBytes = content.getBytes();

                    /*menor que 1024 bytes*/
                    System.arraycopy(contentBytes, 0, data_block, 0, contentBytes.length);

                    //escreve o bloco criado com o conteúdo no arquivo .dat
                    writeBlock(firstblock, data_block);
                }
            }
        }
    }


    //------------------------METODOS DO UNLINK--------------------------------

    //unlink [/caminho/arquivo] - excluir arquivo ou diretorio (o diretorio precisa estar vazio)
    public static void unlink(String path){
        String[] arrOfStr = path.split("/");
        for (String a : arrOfStr) {
            System.out.println(a);
        }
    }


    //------------------------METODOS DO ISDIREMPTY--------------------------------

    //retorna se o Diretorio esta vazio
    public static boolean isDirEmpty(String path){
        return true;
    }


    //------------------------METODOS DO APPEND--------------------------------

    //append "string" [/caminho/arquivo] - anexar dados em um arquivo
    public static void append(String path){
        String[] arrOfStr = path.split("/");
        for (String a : arrOfStr) {
            System.out.println(a);
        }
    }


    //------------------------METODOS DO READ--------------------------------

    //read [/caminho/arquivo] - ler o conteudo de um arquivo
    public static void read(String path){
        String[] arrOfStr = path.split("/");
        for (String a : arrOfStr) {
            System.out.println(a);
        }
    }


    //------------------------MAIN--------------------------------

    public static void main(String[] args) {
        //init();
        fat = readFat();
        shell();

        /*System.out.println("LS EM ROOT: ");
        ls("root");

        System.out.println("\nCRIANDO UM SUBDIRETORIO EM ROOT");
        mkdir("root/oi");

        System.out.println("\nLS EM ROOT: ");
        ls("root");

        System.out.println("\nCRIANDO ARQUIVO EM ROOT");
        String conteudo = "conteudoooooooooooarquivo_dentro_de_root";
        createArchive("root/arquivo", conteudo, conteudo.getBytes().length);

        System.out.println("\nLS EM ROOT: ");
        ls("root");

        System.out.println("\nCRIANDO ARQUIVO EM ROOT/OI");
        conteudo = "conteudoooooooo_arquivo_dentro_de_oi";
        createArchive("root/oi/arquivodeoi", conteudo, conteudo.getBytes().length);

        System.out.println("\nLS EM ROOT: ");
        ls("root");

        System.out.println("\nLS EM ROOT/OI: ");
        ls("root/oi");*/
    }

    //------------------------SHELL--------------------------------

    private static void shell() {
        Scanner scan = new Scanner(System.in);
        boolean running = true;

        while(running) {
            System.out.println("\nDigite o comando desejado. Para sair, digite 'exit'");
            // pega comando inteiro
            String[] command = scan.nextLine().split(" ");
            // separa comando
            String op = command[0];

            switch (op) {
                case "exit":
                    System.out.println("Finalizando sistema");
                    running = false;
                    break;

                case "init":
                    init();
                    System.out.println("Inicialização concluída");
                    break;

                case "ls":
                    if(command.length == 1) {
                        System.out.println("Por favor, insira o caminho específico para executar o comando adequadamente");
                    } else {
                        ls(command[1]);
                    }
                    break;

                case "mkdir":
                    if(command.length == 1) {
                        System.out.println("Por favor, insira o caminho específico para executar o comando adequadamente");
                    } else {
                        mkdir(command[1]);
                    }
                    break;

                case "create":
                    createArchive(command[1], command[2], command[2].getBytes().length);
                    break;

                case "write":
                    writeArchive(command[1], command[2], command[2].getBytes().length);
                    break;

                default:
                    System.out.println("Opção inválida");
            }
        }
    }
}

