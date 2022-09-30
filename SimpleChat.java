/*

Codigo de acordo com o tutorial abaixo. Favor ler para compreender melhor o funcionamento da aplicação.

http://www.jgroups.org/tutorial4/index.html

*/

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.jgroups.util.Util;

import util.Mensagem;

import java.io.*;
import java.util.List;
import java.util.LinkedList;

/*Como o SimpleChat é um ReceiverAdapter, ele é capaz de além de enviar mensagens,também receber mensagens de forma assíncrona */
public class SimpleChat extends ReceiverAdapter {
    JChannel channel;
    String user_name=System.getProperty("user.name", "n/a");
    final List<String> state=new LinkedList<>();

    /*Toda a vez que um peer entra ou sai do grupo é enviado um objeto View, que contém informações sobre todos os peers */
    public void viewAccepted(View new_view) {
        System.out.println("** view: " + new_view);
    }

    /*Função que recebe uma mensagem e faz o print desta. Método é chamado toda a vez que chega uma mensagem, pelo receiver */
    //quem recebe
    public void receive(Message msg) {
        //String line=msg.getSrc() + ": " + msg.getObject();
       // System.out.println(line);
        //aqui é feita a atualização do state, que é compartilhado com todos os peers
        Mensagem m = (Mensagem)msg.getObject();
        switch(m.getOperacao()){
            case "HELLO":
             System.err.println("Chegou a mensagem de Hello" + m);
             Mensagem resposta = new Mensagem("HELLORESPONSE");
             resposta.setParam("msg", "Bem-vindo");
                try {
                    channel.send(msg.getSrc(),resposta);
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            break;
            case "HELLORESPONSE":
            System.err.println("Chegou o responde: "+msg.getObject());
            break;
        }

        synchronized(state) {
            state.add(m.toString());
        }
    }

    /*recebe o state de outro processo */
    public void getState(OutputStream output) throws Exception {
        synchronized(state) {
            Util.objectToStream(state, new DataOutputStream(output));
        }
    }
    /*faz a atualização do state local */
    @SuppressWarnings("unchecked")
    public void setState(InputStream input) throws Exception {
        List<String> list=Util.objectFromStream(new DataInputStream(input));
        synchronized(state) {
            state.clear();
            state.addAll(list);
        }
        System.out.println("received state (" + list.size() + " messages in chat history):");
        list.forEach(System.out::println);
    }

    /*Cria o canal de Comunicação se não existir e passa a ser o coordenador, senão entra no canal */
    private void start() throws Exception {
        //criação do canal e configuração do receiver
        channel=new JChannel().setReceiver(this);
        channel.connect("ChatCluster");
        //faz o pedido do state global ao processo coordenador
        channel.getState(null, 10000);
        //abre event loop para envio de mensagens
        eventLoop();
        channel.close();
    }

    /*Event */ //quem envia 
    private void eventLoop() {
        BufferedReader in=new BufferedReader(new InputStreamReader(System.in));
        while(true) {
            try {
                System.out.print("> "); System.out.flush();
                String line=in.readLine().toLowerCase();
                Mensagem m = new Mensagem("HELLO");
                m.setParam("msg", "Oi!" );

                if(line.startsWith("quit") || line.startsWith("exit")) {
                    break;
                }
                
                Message msg=new Message(null, m);
                channel.send(msg);
            }
            catch(Exception e) {
            }
        }
    }


    public static void main(String[] args) throws Exception {
        new SimpleChat().start();
    }
}