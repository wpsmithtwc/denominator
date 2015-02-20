package denominator.route53;

import com.squareup.okhttp.mockwebserver.MockResponse;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import dagger.Module;
import dagger.Provides;
import denominator.Credentials;
import denominator.Credentials.ListCredentials;
import denominator.DNSApi;
import denominator.Denominator;

import static denominator.CredentialsConfiguration.credentials;

@Test(singleThreaded = true)
public class Route53ProviderDynamicUpdateMockTest {

  MockRoute53Server server;

  String hostedZones = "<ListHostedZonesResponse><HostedZones /></ListHostedZonesResponse>";

  @Test
  public void dynamicEndpointUpdates() throws Exception {
    final AtomicReference<String> url = new AtomicReference<String>(server.url());
    server.enqueue(new MockResponse().setBody(hostedZones));

    DNSApi api = Denominator.create(new Route53Provider() {
      @Override
      public String url() {
        return url.get();
      }
    }, credentials(server.credentials())).api();

    api.zones().iterator();
    server.assertRequest();

    MockRoute53Server server2 = new MockRoute53Server();
    url.set(server2.url());
    server2.enqueue(new MockResponse().setBody(hostedZones));

    api.zones().iterator();

    server2.assertRequest();
    server2.shutdown();
  }

  @Test
  public void dynamicCredentialUpdates() throws Exception {
    server.enqueue(new MockResponse().setBody(hostedZones));

    AtomicReference<Credentials>
        dynamicCredentials =
        new AtomicReference<Credentials>(server.credentials());

    DNSApi
        api =
        Denominator.create(server, new OverrideCredentials(dynamicCredentials)).api();

    api.zones().iterator();

    server.assertRequest().hasHeaderContaining("X-Amzn-Authorization", "accessKey");

    dynamicCredentials.set(ListCredentials.from("accessKey2", "secretKey2", "token"));

    server.credentials("accessKey2", "secretKey2", "token");
    server.enqueue(new MockResponse().setBody(hostedZones));

    api.zones().iterator();

    server.assertRequest().hasHeaderContaining("X-Amzn-Authorization", "accessKey2");
  }

  @BeforeMethod
  public void resetServer() throws IOException {
    server = new MockRoute53Server();
  }

  @AfterMethod
  public void shutdownServer() throws IOException {
    server.shutdown();
  }

  @Module(complete = false, library = true, overrides = true)
  static class OverrideCredentials {

    final AtomicReference<Credentials> dynamicCredentials;

    OverrideCredentials(AtomicReference<Credentials> dynamicCredentials) {
      this.dynamicCredentials = dynamicCredentials;
    }

    @Provides
    public Credentials get() {
      return dynamicCredentials.get();
    }
  }
}
