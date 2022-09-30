/*

Codigo de acordo com o tutorial abaixo. Favor ler para compreender melhor o funcionamento da aplicação.

http://www.jgroups.org/tutorial4/index.html

*/

import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.jgroups.util.Util;

import util.Mensagem;
import util.Status;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedList;

/*Como o SimpleChat é um ReceiverAdapter, ele é capaz de além de enviar mensagens,também receber mensagens de forma assíncrona */
public class Peer extends ReceiverAdapter {
    JChannel channel;
    String user_name = System.getProperty("user.name", "n/a");
    final List<String> state = new LinkedList<>();
    View viewAtual;
    Map<String, Address> usuarios = new HashMap<>();

    /*
     * Toda a vez que um peer entra ou sai do grupo é enviado um objeto View, que
     * contém informações sobre todos os peers
     */
    public void viewAccepted(View new_view) {
        System.out.println("** view: ");
        viewAtual = new_view;
    }

    /*
     * Função que recebe uma mensagem e faz o print desta. Método é chamado toda a
     * vez que chega uma mensagem, pelo receiver
     */
    public void receive(Message msg) {
        // Faz parser do payload de Message para Mensagem
        Mensagem m = (Mensagem) msg.getObject();
        Mensagem resposta = new Mensagem();

        try {
            switch (m.getOperacao()) {
                case "HELLO":
                    System.err.println(" Chegou a mensagem de Hello " + m);
                    resposta = new Mensagem(" HELLORESPONSE ");
                    resposta.setParam("msg ", " Bem-vindo ");
                    try {
                        channel.send(msg.getSrc(), resposta);
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    break;
                case "HELLORESPONSE":
                    System.err.println("Chegou o response: " + msg.getObject());
                    break;
                case "SETNAME":
                    try {
                        // endereço e vincular com o nome que veio do protocolo
                        Address endereco = msg.getSrc();
                        String nome = (String) m.getParam("nome");
                        usuarios.put(nome, endereco);
                        m = new Mensagem("SETNAMERESPONSE");
                        m.setStatus(Status.OK);

                        channel.send(msg.getSrc(), m);
                    } catch (Exception e) {

                    }
                    break;
                case "SETNAMERESPONSE":
                    System.err.println("Identificação foi: ");
                    if (m.getStatus() == Status.OK) {
                        System.err.println("Bem-sucedida");
                    } else {
                        System.err.println("Mal-sucedida");
                    }
                    break;
                case "MSGPRIVADA":
                    String s = "Mensagem Recebida: \n";
                    s += "Origem: " + msg.getSrc() + "\n";
                    m = msg.getObject();
                    s += "Conteudo: " + m.getParam("conteudo") + "\n";

                    m = new Mensagem("MSGPRIVADARESPONSE");
                    m.setStatus(Status.OK);
                    msg = new Message(msg.getSrc(), m);
                    channel.send(msg.getSrc(), m);
                    System.err.println(s);

                    break;
                case "MSGPRIVADARESPONSE":
                    System.err.println("msg recebida com sucesso");
                    break;

                case "SETSALDO":
                    String sald = "Saldo depositado!";

                    sald += "Saldo: " + m.getParam("saldo");

                    m = new Mensagem("SETSALDORESPONSE");
                    m.setStatus(Status.OK);

                    break;
                case "SETSALDORESPONSE":
                    System.err.println("Saldo depositado com sucesso");
                    break;

            }

            // aqui é feita a atualização do state, que é compartilhado com todos os peers
            synchronized (state) {
                state.add(msg.toString()); // é atualizado com a string da mensagem
            }
        } catch (Exception e) {
            System.err.println(" Erro ao receber a msg " + e.getMessage());
        }
    }

    /* recebe o state de outro processo */
    public void getState(OutputStream output) throws Exception {
        synchronized (state) {
            Util.objectToStream(state, new DataOutputStream(output));
        }
    }

    /* faz a atualização do state local */
    @SuppressWarnings("unchecked")
    public void setState(InputStream input) throws Exception {
        List<String> list = Util.objectFromStream(new DataInputStream(input));
        synchronized (state) {
            state.clear();
            state.addAll(list);
        }
        System.out.println("received state (" + list.size() + " messages in chat history):");
        list.forEach(System.out::println);
    }

    /*
     * Cria o canal de Comunicação se não existir e passa a ser o coordenador, senão
     * entra no canal
     */
    private void start() throws Exception {
        // criação do canal e configuração do receiver
        channel = new JChannel().setReceiver(this);
        channel.connect("ChatCluster");
        // faz o pedido do state global ao processo coordenador
        channel.getState(null, 10000);
        // abre event loop para envio de mensagens
        eventLoop();
        channel.close();
    }

    /* Event */
    private void eventLoop() {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        Mensagem m = new Mensagem();
        Message msg = new Message();
        String cont, cont1;
        while (true) {
            try {
                mostraMenu();
                System.out.print("> ");
                System.out.flush();
                String line = in.readLine().toLowerCase();
                // switch para interação com usuário
                switch (line) {
                    case "1":
                        // mensagem do protocolo
                        m = new Mensagem("HELLO");
                        m.setParam("msg", "OI!");
                        // mensagem do JGroup. null = para todos
                        msg = new Message(null, m);// encapsula m no payload da msg
                        channel.send(msg);
                        break;
                    case "2":
                        System.err.println(" Lista de usuarios: ");
                        for (String s : usuarios.keySet()) {
                            System.err.println(s);
                        }
                        break;
                    case "3":
                        // enviar protocolo de identificação
                        m = new Mensagem("SETNAME");
                        String nome = in.readLine().toLowerCase();
                        m.setParam("nome", nome);
                        msg = new Message(null, m);
                        channel.send(msg);

                        break;
                    case "4":
                        // Mensagem pelo endereço
                        List<Address> enderecos = viewAtual.getMembers();
                        for (int i = 0; i < enderecos.size(); i++) {
                            System.err.println(i + " - " + enderecos.get(i));
                        }
                        Integer indice = Integer.parseInt(in.readLine().toLowerCase());

                        String conteudo = in.readLine().toLowerCase();

                        // escrevendo o protocolo
                        m = new Mensagem("MSGPRIVADA");
                        m.setParam("conteudo", conteudo);

                        // escrevendo a msg do Jgroups que encapsula o protocolo
                        msg = new Message(enderecos.get(indice), m);
                        channel.send(enderecos.get(indice), msg);

                        break;
                    case "5":
                        System.err.println("Usuarios identificados: ");
                        for (String s : usuarios.keySet()) {
                            System.err.println(s);

                        }
                        System.err.println("Nome do destionatario > ");
                        String n = in.readLine().toLowerCase();

                        System.err.println("Mensagem > ");
                        conteudo = in.readLine().toLowerCase();

                        // escrevendo o protocolo
                        m = new Mensagem("MSGPRIVADA");
                        m.setParam("conteudo", conteudo);

                        // escrevendo a msg do Jgroups que encapsula o protocolo
                        msg = new Message(usuarios.get(n), m);
                        channel.send(msg);
                        break;
                    case "6":
                        // seta saldo no user
                        // System.err.println("Digite seu saldo: ");
                        // Integer c = in.read();
                        System.err.println("Digite seu saldo: ");
                        cont = in.readLine();

                        System.err.println("Nome do destionatario > ");
                        String destiny = in.readLine().toLowerCase();

                        // escrevendo a msg do Jgroups que encapsula o protocolo
                        // String cont = String.valueOf(c);

                        // escrevendo o protocolo
                        m = new Mensagem("SETSALDO");
                        m.setParam("saldo", cont);

                        msg = new Message(usuarios.get(destiny), m);
                        channel.send(msg);
                        // user a qual deseja enviar saldo
                        break;
                    case "7":
                    
                    System.err.println("Digite o deposito: ");
                    cont1 = in.readLine();
                        
                    System.err.println("Nome do destionatario > ");
                    String destiny1 = in.readLine().toLowerCase();
                        if(usuarios.keySet().equals(destiny1)){
                               int num = Integer.parseInt(cont);
                        }
                    // escrevendo a msg do Jgroups que encapsula o protocolo
                    // String cont = String.valueOf(c);

                    // escrevendo o protocolo
                    m = new Mensagem("SETDEPOSITA");
                    m.setParam("saldo", cont1);

                    msg = new Message(usuarios.get(destiny1), m);
                    channel.send(msg);
                    break;
                    default:
                        System.err.println("Comando errado.");
                }

                if (line.startsWith("quit") || line.startsWith("exit")) {
                    break;
                }
            } catch (Exception e) {
                System.err.println(" Erro ao enviar a msg " + e.getMessage());
                // System.err.println();
            }
        }
    }

    private void mostraMenu() {
        System.err.println("1 - Enviar TESTE");
        System.err.println("2 - Para mostrar usuários");
        System.err.println("3 - Identificar-se");
        System.err.println("4 - Mensagem particular endereço");
        System.err.println("5 - Mensagem particular nome");
        System.err.println("6 - Depositar");
    }

    public static void main(String[] args) throws Exception {
        new Peer().start();
    }
}