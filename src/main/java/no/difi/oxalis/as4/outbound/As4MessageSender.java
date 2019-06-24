package no.difi.oxalis.as4.outbound;

import com.google.inject.Inject;
import no.difi.oxalis.api.outbound.TransmissionRequest;
import no.difi.oxalis.api.outbound.TransmissionResponse;
import no.difi.oxalis.api.settings.Settings;
import no.difi.oxalis.api.tag.Tag;
import no.difi.oxalis.api.timestamp.TimestampProvider;
import no.difi.oxalis.as4.api.MessageIdGenerator;
import no.difi.oxalis.as4.lang.OxalisAs4TransmissionException;
import no.difi.oxalis.as4.util.CompressionUtil;
import no.difi.oxalis.as4.util.Marshalling;
import no.difi.oxalis.as4.util.PeppolConfiguration;
import no.difi.oxalis.commons.http.HttpConf;
import no.difi.oxalis.commons.security.KeyStoreConf;
import org.apache.wss4j.common.WSS4JConstants;
import org.apache.wss4j.common.crypto.Merlin;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.transport.http.HttpComponentsMessageSender;

import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPException;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.StringJoiner;

import static no.difi.oxalis.as4.util.Constants.RSA_SHA256;

public class As4MessageSender {

    private final X509Certificate certificate;
    private final KeyStore keyStore;
    private final Settings<KeyStoreConf> settings;
    private final TimestampProvider timestampProvider;
    private final CompressionUtil compressionUtil;
    private final MessageIdGenerator messageIdGenerator;
    private final Settings<HttpConf> httpConfSettings;
    private final PeppolConfiguration peppolConfiguration;

    @Inject
    public As4MessageSender(X509Certificate certificate,
                            KeyStore keyStore,
                            Settings<KeyStoreConf> settings,
                            TimestampProvider timestampProvider,
                            CompressionUtil compressionUtil,
                            MessageIdGenerator messageIdGenerator,
                            Settings<HttpConf> httpConfSettings,
                            PeppolConfiguration peppolConfiguration) {
        this.certificate = certificate;
        this.keyStore = keyStore;
        this.settings = settings;
        this.timestampProvider = timestampProvider;
        this.compressionUtil = compressionUtil;
        this.messageIdGenerator = messageIdGenerator;
        this.httpConfSettings = httpConfSettings;
        this.peppolConfiguration = peppolConfiguration;
    }

    public TransmissionResponse send(TransmissionRequest request) throws OxalisAs4TransmissionException {
        WebServiceTemplate template = createTemplate(request);
        As4Sender sender = new As4Sender(request, certificate, compressionUtil, messageIdGenerator, peppolConfiguration);
        TransmissionResponseExtractor responseExtractor = new TransmissionResponseExtractor(request, timestampProvider);
        As4TransmissionResponse as4TransmissionResponse = template.sendAndReceive(request.getEndpoint().getAddress().toString(), sender, responseExtractor);

        if(as4TransmissionResponse.getTransmissionException() != null){
            throw as4TransmissionResponse.getTransmissionException();
        }

        return as4TransmissionResponse;

    }

    private SaajSoapMessageFactory createSoapMessageFactory() {
        try {
            return new SaajSoapMessageFactory(MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL));
        } catch (SOAPException e) {
            throw new RuntimeException("Error creating SoapMessageFactory", e);
        }
    }

    private WebServiceTemplate createTemplate(TransmissionRequest request) {
        As4WebServiceTemplate template = new As4WebServiceTemplate(createSoapMessageFactory());
        template.setMarshaller(Marshalling.getInstance());
        template.setUnmarshaller(Marshalling.getInstance());
        template.setMessageSender(createMessageSender());
        template.setInterceptors(new ClientInterceptor[]{
                createWsSecurityInterceptor(request.getEndpoint().getCertificate(), request.getTag()),
                new ReferenceValidatorInterceptor()
        });
        return template;
    }

    private ClientInterceptor createWsSecurityInterceptor(X509Certificate certificate, Tag tag) {

        Merlin crypto = new Merlin();
        crypto.setCryptoProvider(BouncyCastleProvider.PROVIDER_NAME);
        crypto.setKeyStore(keyStore);

        KeyStore endpointKeystore;
        try {
            endpointKeystore = KeyStore.getInstance("JKS");
            endpointKeystore.load(null, "endpoint".toCharArray());
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("Could not instantiate keystore for certificate from endpoint", e);
        }

        try {
            endpointKeystore.setCertificateEntry("endpoint", certificate);
        } catch (KeyStoreException e) {
            throw new RuntimeException("Could not add certificate to endpoint keystore", e);
        }

        Merlin endpointCrypto = new Merlin();
        endpointCrypto.setCryptoProvider(BouncyCastleProvider.PROVIDER_NAME);
        endpointCrypto.setKeyStore(endpointKeystore);

        String alias = settings.getString(KeyStoreConf.KEY_ALIAS);
        String password = settings.getString(KeyStoreConf.PASSWORD);

        WsSecurityInterceptor interceptor = new WsSecurityInterceptor();

        interceptor.setSecurementPassword(password);


        PeppolConfiguration peppolConfiguration = tag instanceof PeppolConfiguration ? (PeppolConfiguration) tag : new PeppolConfiguration();

        StringJoiner actions = new StringJoiner(" ");
        for(String action : peppolConfiguration.getActions()){
            actions.add(action);
        }

        interceptor.setSecurementActions(actions.toString());



        interceptor.setSecurementSignatureUser(alias);
        interceptor.setSecurementSignatureCrypto(crypto);
        interceptor.setValidationSignatureCrypto(crypto);
        interceptor.setSecurementSignatureAlgorithm(RSA_SHA256);
        interceptor.setSecurementSignatureDigestAlgorithm(DigestMethod.SHA256);
        interceptor.setSecurementSignatureKeyIdentifier("DirectReference");
        interceptor.setSecurementSignatureParts("{}{}Body; {}cid:Attachments; {}{http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/}Messaging");

        interceptor.setSecurementEncryptionUser("endpoint");
        interceptor.setSecurementEncryptionCrypto(endpointCrypto);
        interceptor.setSecurementEncryptionSymAlgorithm(WSS4JConstants.AES_128_GCM);
        interceptor.setSecurementEncryptionKeyIdentifier("SKIKeyIdentifier");
        interceptor.setEnableSignatureConfirmation(true);

        interceptor.setSecurementEncryptionParts("{}cid:Attachments");

        interceptor.setSecurementEncryptionKeyTransportAlgorithm(WSS4JConstants.KEYTRANSPORT_RSAOAEP_XENC11);

        interceptor.setSecurementMustUnderstand(true);

        try {
            interceptor.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException("Could not set up security interceptor", e);
        }

        return interceptor;
    }

    private HttpComponentsMessageSender createMessageSender() {
        HttpComponentsMessageSender sender = new HttpComponentsMessageSender();
        sender.setReadTimeout(httpConfSettings.getInt(HttpConf.TIMEOUT_READ));
        sender.setConnectionTimeout(httpConfSettings.getInt(HttpConf.TIMEOUT_CONNECT));
        return sender;
    }
}
