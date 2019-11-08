## Oxalis outside PEPPOL

Oxalis is made to serve as an AccessPoint in the PEPPOL network.
If you intend to use Oxalis sending messages outside PEPPOL you should implement your own version of the relevant [Oxalis extension points](https://github.com/difi/oxalis/blob/master/doc/extension-points.adoc).

### CEF connectivity test

The following Oxalis features needs to be changed to be able to exchange messages with the CEF connectivity test (RC-7 and onwards):
* HeaderParser
  ```
  oxalis.header.parser=dummy
  ```
  See [Oxalis extention-points](https://github.com/difi/oxalis/blob/master/doc/extension-points.adoc)
  In its default setup Oxalis will extract the SBDH form the payload. Sins the CEF connectivity test does not send messages with a SBDH, this will fail.
  To pass the test you can disable the parsing of the SBDH header by adding the following line to your configuration file:

* CEF pMode
  ```
  oxalis.as4.type=cef-connectivity
  ```
  This setting changes three tings in the AS4 header resulting from the diffrent pModes:
  1. Removes the reference to the PEPPOL TIA agreement
  2. Renamed the serviceType for the transmission to "urn:oasis:names:tc:ebcore:partyid-type:unregistered"
  3. Renamed the partyIdType of the sender and reciever to "urn:oasis:names:tc:ebcore:partyid-type:unregistered"
  
### CEF-Connectivity using Oxalis-Standalone

<details>
  <summary>Instruction to send CEF connectivity message</summary>
  
  <br/>
  <p>
  To perform the CEF-Connectivity test send this file using these parameters <code>-f &lt;path to filr&gt; -u &lt;http address to CEF&gt; -cert &lt;path to CEF certificate&gt;</code>
  Use of the additional override commands will add PEPPOL prefixes to the values that will breake the connectivity test.
  </p>

``` XML
<?xml version="1.0" encoding="UTF-8"?>
<StandardBusinessDocument xmlns="http://www.unece.org/cefact/namespaces/StandardBusinessDocumentHeader">
    <StandardBusinessDocumentHeader>
        <HeaderVersion>1.0</HeaderVersion>
        <Sender>
            <!-- This Sender section describes the PEPPOL Sender -->
            <!-- It corresponds to an OriginalSender in AS4 -->
          <Identifier>urn:oasis:names:tc:ebcore:partyid-type:unregistered:C1</Identifier>
      </Sender>
      <Receiver>
           <!-- This Sender section describes the PEPPOL Receiver -->
           <!-- It corresponds to an FinalRecipient in AS4 -->
           <Identifier>urn:oasis:names:tc:ebcore:partyid-type:unregistered:C4</Identifier>
        </Receiver>
        <DocumentIdentification>
            <!-- This DocumentIdentification section describes the content of the payload -->
            <!-- It is noe essential to the CEF connectivity test, but is needed for a valid SBDH -->

            <Standard>NONE</Standard>
            <TypeVersion>1.0</TypeVersion>
            <InstanceIdentifier>555bcb4c-940b-4694-9b90-d9b0ae1e937b</InstanceIdentifier>
            <Type>CEF Connectivity test</Type>
            <CreationDateAndTime>2019-10-30T11:20:05.304+02:00</CreationDateAndTime>
        </DocumentIdentification>
        <BusinessScope>
            <Scope>
                <!-- This Scope section describes the PEPPOL DocumentType -->
                <!-- It corresponds to an Action in AS4 (PMode[1].BusinessInfo.Action) -->

                <Type>DOCUMENTID</Type>
                <!-- We add an empty Identifier element to set the 'DocumentIdentifier Schema' to en empty string -->
                <!-- If we do not do this Oxalis-Standalone will add a default 'DocumentType Schema' that will prefix the Action with "busdox-docid-qns", or what is defined in this element, and "::" -->
                <Identifier/>
                <InstanceIdentifier>connectivity::cef##connectivity::submitMessage</InstanceIdentifier>
            </Scope>
            <Scope>
                <!-- This Scope section describes the PEPPOL Process -->
                <!-- It corresponds to an Service in AS4 (PMode[1].BusinessInfo.Service) -->

                <Type>PROCESSID</Type>
                <!-- This Identifier describes the PEPPOL 'Process Schema' and corresponds to a Service.Type in AS4 (PMode[].BusinessInfo.Service.type) -->
                <Identifier>e-delivery</Identifier>
                <!-- This InstanceIdentifier describes the PEPPOL 'Process Value' and corresponds to an Service in AS4 (PMode[1].BusinessInfo.Service) -->
                <InstanceIdentifier>http://ec.europa.eu/edelivery/services/connectivity-service</InstanceIdentifier>
            </Scope>
        </BusinessScope>
    </StandardBusinessDocumentHeader>
    <Request> eDelivery AS4 Connectivity test. Sending Message </Request>
</StandardBusinessDocument>
```
</details>

Oxalis-Standalone is a commandline wrapper around Oxalis-Outbound that facilitate sending of PEPPOL messages.

The base functionallity of Standalone is to send files that is in the form of a Standard Bussines Document (SBD). SBD files starts with a Standard Bussines Ducument Header (SBDH) that describes the message, sender, and reciever and some more. Standalone reads this information and uses it to perform the transmission. 

The standalone component also has the ability to override these settings, this is mostly in place to facilitate testing of your own innbound instalation.

One of the values that is extracted and parsed is the DocumentType (This corresponds to an Action in AS4 terms). This value has to be in the following form to be accespted: <em>TextAndNumbers::TextAndNumbers##TextAndNumbers::TextAndNumbers</em>. This is the main hurdle to using Standalone to perform CEF-Connectivity test. To work around this issue we have added a feature that stripps the parts of the action taht does not conform to the conenctivity test.

DocumentTypes on the form of `connectivity::cef##connectivity::submitMessage` will be converted to `submitMessage` by stripping avay the unwanted prefix. This only works for this prefix.
 
### CEF-Connectivity using Custom Integration

<details>
  <summary>Types and objects involved in a transmission</summary>
  
<dl>
  <dt><a href=https://github.com/difi/vefa-peppol/blob/master/peppol-common/src/main/java/no/difi/vefa/peppol/common/model/Header.java#L67>Header</a></dt>
  <dd>The dynamic information about the transmission. Either infeared by a HeaderParser, or provided by other means
    <table summary="Mapping between PMode properties and Header values">
      <thead>
        <tr>
          <th>PMode</th>
          <th>Header fields</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td>PMode.BusinessInfo.Service</td>
          <td>Header.<a href=https://github.com/difi/vefa-peppol/blob/master/peppol-common/src/main/java/no/difi/vefa/peppol/common/model/DocumentTypeIdentifier.java#L39>documentType</a>.identifier</td>
        </tr>
        <tr>
          <td>PMode.BusinessInfo.Service.type</td>
          <td>Header.<a href=https://github.com/difi/vefa-peppol/blob/master/peppol-common/src/main/java/no/difi/vefa/peppol/common/model/DocumentTypeIdentifier.java#L39>documentType</a>.<a href=https://github.com/difi/vefa-peppol/blob/master/peppol-common/src/main/java/no/difi/vefa/peppol/common/model/Scheme.java#L32>schema</a></td>
        </tr>
         <tr>
          <td>PMode.BusinessInfo.Action</td>
           <td>Header.<a href=https://github.com/difi/vefa-peppol/blob/master/peppol-common/src/main/java/no/difi/vefa/peppol/common/model/ProcessIdentifier.java#L79>proces</a></td>
        </tr>
         <tr>
          <td>PMode.BusinessInfo.Properties[@name=originalSender]</td>
          <td>Header.<a href=https://github.com/difi/vefa-peppol/blob/master/peppol-common/src/main/java/no/difi/vefa/peppol/common/model/ParticipantIdentifier.java#L43>sender</td>
        </tr>
         <tr>
          <td>PMode.BusinessInfo.Properties[@name=finalRecipient]</td>
           <td>Header.<a href=https://github.com/difi/vefa-peppol/blob/master/peppol-common/src/main/java/no/difi/vefa/peppol/common/model/ParticipantIdentifier.java#L43>receiver</a></td>
        </tr>
      </tbody>
    </table>
  </dd>
  
  <dt><a href=https://github.com/difi/vefa-peppol/blob/master/peppol-common/src/main/java/no/difi/vefa/peppol/common/model/Endpoint.java>Endpoint</a></dt>
  <dd>Contains address and certificate for the reciever (Access 
    Point), and a TransmissionProtocol ("<em>peppol-transport-as4-v2_0</em>" to target this AS4 plugin). Either provided by an SMP lookup (based on values from the Header) or provided by other means

   <table summary="Mapping between PMode properties and Endpoint values">
      <thead>
        <tr>
          <th>PMode</th>
          <th>Endpoint fields</th>
          <th>Note</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td>PMode.Responder.Party</td>
          <td rowspan=2>Endpoint.certificate</td>
          <td>Value taken from the Commen Name (CN) of the certificate</td>
        </tr>
        <tr>
          <td>PMode.Security.Encryption.Certificate</td>
          <td></td>
        </tr>      
        <tr>
          <td>PMode.Protocol.Address</td>
          <td>Endpoint.address</td>
          <td></td>
        </tr>
      </tbody>
    </table>
  </dd>
  
  <dt>InputStream</dt>
  <dd>The payload to be sendt. What goes here is delivered to the recipient</dd>
</dl>

The TransmissionRequest describes the tramsmission that is to be sent.

TransmissionRequest consists of thre objects:

</details>