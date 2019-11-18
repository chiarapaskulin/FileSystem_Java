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
import java.util.ArrayList;
import java.util.Scanner;

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
    static short[] fat = new short[blocks]; //2048 representacoes de bloco de 2 bytes cada = 4096 bytes = 4 blocos
    /* data block */
    static byte[] data_block = new byte[block_size]; //1 bloco local de tamanho 1024 bytes


    //------------------------METODOS DE MANIPULACAO DE MEMORIA--------------------------------

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


    //------------------------METODO DE INIT--------------------------------

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


    //------------------------METODOS GERAIS--------------------------------

    //devolve a primeira entrada vazia (com valor 0) da FAT e -1 se estiver cheia
    private static short first_free_FAT_entry(){
        //i começa em 5 pois de 0 a 3 são os blocos da FAT e 4 é o bloco do root
        for(int i=5; i<fat.length; i++){
            if(fat[i] == 0) return (short) i;
        }

        //-1 deve ser tratado na chamada do método, pois indica que não há lugar na FAT
        return -1;
    }

    //devolve a primeira entrada vazia (com valor 0) do diretorioe -1 se estiver cheio
    private static short first_free_dir_entry(int blocoAtual){
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
    private static boolean does_entry_exists(int blocoAtual, String path){
        //confere cada entrada de diretório do blocoAtual
        for(int i=0; i<32; i++){
            DirEntry entry = readDirEntry(blocoAtual, i);
            if(entry.attributes!=0) {
                byte[] b = new byte[1];
                String dirName = "";
                for (int k = 0; k < entry.filename.length; k++) {
                    if (entry.filename[k] != 0) {
                        b[0] = entry.filename[k];
                        try {
                            dirName += new String(b, "UTF-8");
                        } catch (Exception e) {
                        }
                    } else break;
                }
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
    private static int roundUp(double num){
        if ((num-(int)num) > 0.0 ){
            num += 1;
        }
        return (int)num;
    }


    //------------------------METODOS DO LS--------------------------------

    //ls [/caminho/diretorio] - listar diretorio
    public static void ls(String path){
        String[] arrOfStr = path.split("/");
        //passa o path completo
        //a primeira posicao do array é o diretorio ATUAL
        //o blocoAtual é o número do bloco do diretorio ATUAL
        followUntilFindDir(arrOfStr,(short) 4);
    }

    //vai acessando os subdiretorios até o ultimo e chama accessAndListDir para listar o ultimo diretorio
    private static void followUntilFindDir(String[] path, short blocoAtual){
        //se é o ultimo diretorio do path, acessa seu diretorio pai, acessa ele e lista ele
        if(path.length==1) accessAndListDir(path[0], blocoAtual);
        else{
            boolean found = false;

            //nome do diretorio que eu estou procurando é pego no path[1], porque path[0] é o atual
            String arcName = path[1];

            //confere cada entrada de diretório do blocoAtual
            for (int i = 0; i < 32 && !found; i++){
                DirEntry entry = readDirEntry(blocoAtual, i);
                byte[] b = new byte[1];
                String dirName = "";
                for(int k=0; k<entry.filename.length; k++){
                    if(entry.filename[k]!=0){
                        b[0] = entry.filename[k];
                        try {
                            dirName+= new String(b, "UTF-8");
                        }catch(Exception e){}
                    }
                    else break;
                }

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
                    s = new String(dir_entry.filename, "UTF-8");
                }catch (Exception e){};
                System.out.println(s);
            }
        }
    }


    //------------------------METODOS DO MKDIR--------------------------------

    //mkdir [/caminho/diretorio] - criar diretorio
    public static void mkdir(String path){
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
        followUntilCreateDir(newPath,(short) 4);
    }

    //vai acessando os subdiretorios até o ultimo e chama accessAndCreateDir para criar o ultimo
    private static void followUntilCreateDir(String[] path, short blocoAtual){
        //se é o ultimo diretorio do path, o que tem que ser criado, acessa o blocoAtual (diretorio do que tem que ser criado) e cria o mesmo
        if(path.length==1) accessAndCreateDir(path[0], blocoAtual);
        else {
            boolean found = false;

            //nome do diretorio que eu estou procurando no blocoAtual
            String arcName = path[0];

            //confere cada entrada de diretório do blocoAtual para ver se acha nele o diretorio que eu estou procurando
            for (int i = 0; i < 32 && !found; i++) {
                DirEntry entry = readDirEntry(blocoAtual, i);
                byte[] b = new byte[1];
                String dirName = "";
                for(int k=0; k<entry.filename.length; k++){
                    if(entry.filename[k]!=0){
                        b[0] = entry.filename[k];
                        try {
                            dirName+= new String(b, "UTF-8");
                        }catch(Exception e){}
                    }
                    else break;
                }

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
        if(does_entry_exists(blocoAtual, path)){
            System.out.println("O arquivo/entrada de diretório chamado ''" + path + "'' já existe");

            //se o diretorio não existe, cria ele
        }else{
            //procura a primeira entrada de diretorio vazia para criar o subdiretorio
            int entradaDeDirVazia = first_free_dir_entry(blocoAtual);

            //se não achou uma entrada de diretorio vazia, avisa que ele esta cheio
            if(entradaDeDirVazia==-1){
                System.out.println("O diretório está cheio");

                //se achou uma entrada de diretorio vaiza, prossegue
            }else{
                //procura a primeira entrada livre da FAT
                short firstblock = first_free_FAT_entry();

                //return -1 significa que a FAT está cheia, então para de processar
                if(firstblock==-1){
                    System.out.println("A FAT está cheia");

                }else{
                    //define a entrada firstblock da FAT como utilizada (fim de arquivo 0x7fff)
                    fat[firstblock]=0x7fff;
                    //atualiza a FAT no arquivo .dat
                    writeFat("filesystem.dat", fat);

                    //cria a entrada de diretorio para adicionar na entrada de diretorio vazia do blocoAtual
                    DirEntry dir_entry = new DirEntry();
                    String name = path;
                    byte[] namebytes = name.getBytes();
                    for (int j = 0; j < namebytes.length; j++) {
                        dir_entry.filename[j] = namebytes[j];
                    }
                    //define informacoes da entrada de diretorio
                    dir_entry.attributes = 0x02;
                    dir_entry.first_block = firstblock;
                    dir_entry.size = 0;
                    //escreve a entrada de diretorio criada na entrada de diretorio i do blocoAtual
                    writeDirEntry(blocoAtual, entradaDeDirVazia, dir_entry);

                    //cria um bloco completamente VAZIO
                    for (int j = 0; j < block_size/*1024 bytes*/; j++) {
                        data_block[j] = 0;
                    }

                    //escreve o bloco VAZIO criado no arquivo .dat
                    writeBlock("filesystem.dat", firstblock, data_block);
                    writeFat("filesystem.dat", fat);
                }
            }
        }
    }


    //------------------------METODOS DO CREATEARCHIVE--------------------------------

    //create [/caminho/arquivo] - criar arquivo
    public static void createArchive(String path, String content, int size){
        String[] arrOfStr = path.split("/");
        //passa o path completo
        //a primeira posicao do array é o diretorio ATUAL
        //o blocoAtual é o número do bloco do diretorio ATUAL
        followUntilCreateArchive(arrOfStr,(short) 4, content, size);
    }

    //vai acessando os subdiretorios até o penultimo e chama accessAndCreateArchive para criar o ultimo dentro do penultimo
    private static void followUntilCreateArchive(String[] path, short blocoAtual, String content, int size){
        //se [0] é o ultimo diretorio do path e [1] é o arquivo que tem que ser criado, acessa ele e cria o arquivo
        if(path.length==2) accessAndCreateArchive(path, blocoAtual, content, size);
        else{
            boolean found = false;

            //nome do diretorio que eu estou procurando é pego no path[1], porque path[0] é o atual
            String arcName = path[1];

            //confere cada entrada de diretório do blocoAtual
            for (int i = 0; i < 32 && !found; i++) {
                DirEntry entry = readDirEntry(blocoAtual, i);
                byte[] b = new byte[1];
                String dirName = "";
                for(int k=0; k<entry.filename.length; k++){
                    if(entry.filename[k]!=0){
                        b[0] = entry.filename[k];
                        try {
                            dirName+= new String(b, "UTF-8");
                        }catch(Exception e){}
                    }
                    else break;
                }

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
                    followUntilCreateArchive(newPath, entry.first_block, content, size);
                }
            }

            //printa que não achou o diretorio path[1], que é o que está sendo procurado no atual path[0]
            if (!found) System.out.println("Não há nenhum diretório chamado /" + path[1]);
        }
    }

    //cria o diretorio descrito em path[1] dentro de path[0]
    private static void accessAndCreateArchive(String[] path, short blocoAtual, String content, int size){
        boolean found = false;

        //nome do diretorio atual do bloco atual
        byte[] file = path[0].getBytes();

        if(does_entry_exists(blocoAtual,path[1])){
            System.out.println("O arquivo/entrada de diretório chamado ''" + path[1] + "'' já existe");
            //se não tem nenhuma entrada de diretório com esse nome, cria o arquivo
        }else{
            //procura a primeira entrada de diretorio vazia para criar o arquivo
            int entradaDeDirVazia = first_free_dir_entry(blocoAtual);

            //se não achou uma entrada de diretorio vazia, avisa que ele esta cheio
            if(entradaDeDirVazia==-1){
                System.out.println("O diretório está cheio");

                //se achou uma entrada de diretorio vazia, prossegue com a criacao do arquivo
            }else {
                //procura a primeira entrada livre da FAT
                short firstblock = first_free_FAT_entry();
                //return 0 significa que a FAT está cheia, então para de processar
                if(firstblock==-1){
                    System.out.println("A FAT está cheia");
                }else{
                    //faz um processamento especial para um arquivo maior que 1024 bytes
                    if(size>1024){
                        //define a quantidade de blocos que terão que ser utilizados
                        int qt_blocos = roundUp(size/1024);

                        //cria um Array de números de blocos para facilitar a manipulacao
                        ArrayList<Short> blocosFAT = new ArrayList<>();

                        //adiciona 0x7fff no primeiro bloco (para indicar que ele está em uso), adiciona ele na lista e subtrai a quantidade de blocos necessaria
                        blocosFAT.add(firstblock);
                        fat[firstblock] = 0x7fff;
                        qt_blocos--;

                        //procura o proximo bloco vazio, adiciona ele na lista e indica que ele está em uso; subtrai a qtidade de blcoos necessaria
                        for(int i=0; i<qt_blocos; i++){
                            short nextblock = first_free_FAT_entry();
                            blocosFAT.add(nextblock);
                            fat[nextblock] = 0x7fff;
                            qt_blocos--;
                        }

                        //depois que todos os blocos estão marcados como 0x7fff (final de arquivo),
                        //marcamos de trás pra frente a ligação de um com os outros
                        //o ultimo bloco já está marcado como fim de arquivo, o que é correto (por isso começamos com blocosFAT.size()-2
                        //agora devemos marcar o penultimo bloco com o numero do ultimo e assim por diante
                        for(int i=blocosFAT.size()-2; i>=0; i++){
                            short b_atual = blocosFAT.get(i);
                            short b_anterior = blocosFAT.get(i+1);
                            fat[b_atual] = b_anterior;
                        }

                        //atualiza a FAT no arquivo .dat
                        writeFat("filesystem.dat", fat);

                        //cria a entrada de diretorio com o arquivo para adicionar na entrada de diretorio do blocoAtual
                        DirEntry dir_entry = new DirEntry();
                        String name = path[1];
                        byte[] namebytes = name.getBytes();
                        for (int j = 0; j < namebytes.length; j++) {
                            dir_entry.filename[j] = namebytes[j];
                        }
                        //define informacoes da entrada de diretorio
                        dir_entry.attributes = 0x01; //arquivo
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
                            writeBlock("filesystem.dat", blocosFAT.get(i), data_block);
                        }

                    }else {
                        //define a entrada firstblock da FAT como utilizada (fim de arquivo 0x7fff)
                        fat[firstblock] = 0x7fff;
                        //atualiza a FAT no arquivo .dat
                        writeFat("filesystem.dat", fat);

                        //cria a entrada de diretorio com o arquivo para adicionar na entrada de diretorio do blocoAtual
                        DirEntry dir_entry = new DirEntry();
                        String name = path[1];
                        byte[] namebytes = name.getBytes();
                        for (int j = 0; j < namebytes.length; j++) {
                            dir_entry.filename[j] = namebytes[j];
                        }
                        //define informacoes da entrada de diretorio
                        dir_entry.attributes = 0x01; //arquivo
                        dir_entry.first_block = firstblock;
                        dir_entry.size = size;
                        //escreve a entrada de diretorio criada na entrada de diretorio i do blocoAtual
                        writeDirEntry(blocoAtual, entradaDeDirVazia, dir_entry);

                        //cria um bloco com o conteúdo que foi passado por parâmetro
                        byte[] contentBytes = content.getBytes();
                        for (int j = 0; j < contentBytes.length/*menor que 1024 bytes*/; j++) {
                            data_block[j] = contentBytes[j];
                        }
                        //escreve o bloco criado com o conteúdo no arquivo .dat
                        writeBlock("filesystem.dat", firstblock, data_block);
                    }
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


    //------------------------METODOS DO WRITE--------------------------------

    //write "string" [/caminho/arquivo] - escrever dados em um arquivo (sobrescrever dados)
    public static void write(String path){
        String[] arrOfStr = path.split("/");
        for (String a : arrOfStr) {
            System.out.println(a);
        }
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

    public static void main(String args[]) {
        //init();
        fat = readFat("filesystem.dat");
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

    public static void shell() {
        Scanner scan = new Scanner(System.in);
        boolean running = true;

        while(running) {
            System.out.println("\nSelecione a opção desejada:");
            System.out.println("1. Inicializar o sistema (formatar)");
            System.out.println("2. Listar diretório");
            System.out.println("3. Criar diretório");
            System.out.println("4. Criar arquivo");
            System.out.println("5. Excluir arquivo/diretório");
            System.out.println("6. Sobrescrever arquivo");
            System.out.println("7. Anexar dados a um arquivo");
            System.out.println("8. Ler arquivo");
            System.out.println("0. Sair");

            int op = scan.nextInt();

            switch (op) {
                case 0:
                    System.out.println("Finalizando sistema");
                    running = false;
                    break;

                case 1:
                    init();
                    System.out.println("Inicialização concluída");
                    break;

                case 2:
                    System.out.println("Digite o caminho completo do diretório");
                    String ls = scan.next();
                    ls(ls);
                    break;

                case 3:
                    System.out.println("Digite o caminho completo do novo diretório");
                    String mkdir = scan.next();
                    mkdir(mkdir);
                    System.out.println("Arquivo criado com sucesso");
                    break;

                case 4:
                    System.out.println("Digite o caminho completo do novo arquivo (incluindo o nome do arquivo)");
                    String archivePath = scan.next();
                    System.out.println("Digite o conteúdo do arquivo");
                    String archiveContent = scan.next();
                    createArchive(archivePath, archiveContent, archiveContent.getBytes().length);
                    break;

                    default:
                        System.out.println("Opção inválida");
            }

        }
    }
}

