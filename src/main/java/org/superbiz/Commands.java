/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.superbiz;

import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import org.tomitribe.crest.api.Command;
import org.tomitribe.crest.api.Option;
import org.tomitribe.crest.api.Required;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Properties;
import java.util.Random;

public class Commands {
    private static final char[] DATA = createData();
    private static final int DATA_SIZE = 1024 * 1024 * 1; // 1 MiB

    private static char[] createData() {
        final char[] validChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
        final StringBuilder sb = new StringBuilder(DATA_SIZE);
        final Random random = new Random();

        for (int i = 0; i < DATA_SIZE; i++) {
            sb.append(validChars[random.nextInt(validChars.length)]);
        }

        return sb.toString().toCharArray();
    }

    @Command("consume")
    public void consume(@Required @Option("uri") final String uri,
                        @Required @Option("dest") final String destination,
                        @Option("username") final String username,
                        @Option("password") final String password) {

        final ConnectionFactory cf = getConnectionFactory(uri);

        try (final Connection conn = getConnection(cf, username, password);
             final Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            conn.start();
            Destination dest = getDestination(uri, destination);
            MessageConsumer consumer = sess.createConsumer(dest);

            while (true) {
                final TextMessage msg = (TextMessage) consumer.receive();
                System.out.println(msg.getText());
            }
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    private Destination getDestination(String uri, final String destination) {
        if (destination == null) {
            throw new NullPointerException("Destination cannot be null");
        }

        final Properties p = new Properties();
        p.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.jndi.ActiveMQInitialContextFactory");
        p.setProperty(Context.PROVIDER_URL, uri);

        try {
            final InitialContext initialContext = new InitialContext(p);
            return (Destination) initialContext.lookup(destination);
        } catch (Exception e) {
            // try and do this without JNDI
        }

        if (destination.toLowerCase().startsWith("queue://")) {
            return new ActiveMQQueue(destination.substring(8));
        }

        if (destination.toLowerCase().startsWith("topic://")) {
            return new ActiveMQTopic(destination.substring(8));
        }

        throw new RuntimeException(destination + " not found");
    }

    @Command("produce")
    public void produce(@Required @Option("uri") final String uri,
                        @Required @Option("dest") final String destination,
                        @Required @Option("message") final String payload,
                        @Required @Option("count") final Integer count,
                        @Option("username") final String username,
                        @Option("password") final String password) {

        final ConnectionFactory cf = getConnectionFactory(uri);

        try (final Connection conn = getConnection(cf, username, password);
             final Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            conn.start();
            Destination dest = getDestination(uri, destination);
            final MessageProducer producer = sess.createProducer(dest);

            for (int i = 0; i < count; i++) {
                producer.send(sess.createTextMessage(payload));
            }

        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    @Command("produce-random")
    public void produce(@Required @Option("uri") final String uri,
                        @Required @Option("dest") final String destination,
                        @Required @Option("message-size") final Integer payloadSize,
                        @Required @Option("count") final Integer count,
                        @Option("username") final String username,
                        @Option("password") final String password) {

        final ConnectionFactory cf = getConnectionFactory(uri);

        try (final Connection conn = getConnection(cf, username, password);
             final Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            conn.start();
            Destination dest = getDestination(uri, destination);
            final MessageProducer producer = sess.createProducer(dest);

            for (int i = 0; i < count; i++) {
                final String payload = createRandomPayload(payloadSize);
                producer.send(sess.createTextMessage(payload));
            }

        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    private String createRandomPayload(final Integer payloadSize) {
        final Random random = new Random();
        final StringBuilder sb = new StringBuilder(payloadSize);
        sb.append("*");

        int remaining = payloadSize - 2;
        while (remaining > 0) {
            final int offset = random.nextInt(DATA.length);
            if ((DATA.length - offset) > remaining) {
                sb.append(DATA, offset, remaining);
                remaining = 0;
            } else {
                System.out.println(String.format("Size: %d, offset: %d, length: %d, end %d", DATA.length, offset, (DATA.length - offset - 1), offset + (DATA.length - offset - 1)));
                sb.append(DATA, offset, (DATA.length - offset));
                remaining -= (DATA.length - offset);
            }
        }

        sb.append("*");
        return sb.toString();
    }

    private Connection getConnection(final ConnectionFactory cf, final String username, final String password) throws JMSException {
        if (username == null && password == null) {
            return cf.createConnection();
        } else {
            return cf.createConnection(username, password);
        }
    }

    private ConnectionFactory getConnectionFactory(final String uri) {
        try {
            ConnectionFactory cf;
            final Properties p = new Properties();
            p.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.jndi.ActiveMQInitialContextFactory");
            p.setProperty(Context.PROVIDER_URL, uri);

            final InitialContext initialContext = new InitialContext(p);
            cf = (ConnectionFactory) initialContext.lookup("ConnectionFactory");
            return cf;
        } catch (NamingException e) {
            return null;
        }
    }



}
