/* Enhanced NFLOG implementation for AFWall+
 * (C) 2012 Pragmatic Software - Original implementation
 * (C) 2025 AFWall+ Project - Enhancements and optimizations
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/
 *
 * Enhanced features:
 * - Improved memory management and leak prevention
 * - Better error handling and recovery
 * - Optimized interface name caching
 * - Enhanced buffer management
 * - Signal handling for graceful shutdown
 * - Backward compatibility maintained
 */

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <time.h>
#include <fcntl.h>
#include <arpa/inet.h>
#include <signal.h>
#include <errno.h>

#include <libmnl/libmnl.h>
#include <linux/netfilter.h>
#include <linux/netfilter/nfnetlink.h>
#include <linux/ip.h>
#include <linux/in.h>
#include <linux/if.h>
#include <linux/ipv6.h>
#include <linux/if_ether.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/select.h>

#ifndef aligned_be64
#define aligned_be64 u_int64_t __attribute__((aligned(8)))
#endif

#include <linux/netfilter/nfnetlink_log.h>

/*
 * Portable protocol header definitions.
 * The Android NDK (bionic) does not provide struct tcphdr / udphdr / icmphdr /
 * icmp6hdr via <linux/tcp.h> etc. in a way that is compatible with static
 * linking.  Define minimal versions here so the code builds with both GNU
 * cross-compilers and the NDK clang toolchain.
 */
#ifndef AFWALL_PROTO_HDRS
#define AFWALL_PROTO_HDRS

struct tcphdr {
    uint16_t source;
    uint16_t dest;
    uint32_t seq;
    uint32_t ack_seq;
    uint16_t res1:4, doff:4, fin:1, syn:1, rst:1, psh:1, ack:1, urg:1, ece:1, cwr:1;
    uint16_t window;
    uint16_t check;
    uint16_t urg_ptr;
};

struct udphdr {
    uint16_t source;
    uint16_t dest;
    uint16_t len;
    uint16_t check;
};

struct icmphdr {
    uint8_t  type;
    uint8_t  code;
    uint16_t checksum;
    union {
        struct { uint16_t id; uint16_t sequence; } echo;
        uint32_t gateway;
    } un;
};

struct icmp6hdr {
    uint8_t  icmp6_type;
    uint8_t  icmp6_code;
    uint16_t icmp6_cksum;
    union {
        struct { uint16_t id; uint16_t sequence; } echo;
        uint32_t data32[1];
    } icmp6_dataun;
};

#endif /* AFWALL_PROTO_HDRS */

// Enhanced configuration constants
#define MAX_NETDEVICES 64  // Increased from 32 for better device support
#define INTERFACE_CACHE_TTL 300  // Cache interfaces for 5 minutes
#define DEFAULT_BUFFER_SIZE (32 * 1024)  // 32KB default buffer

// Enhanced structures for interface caching
struct interface_cache_entry {
    int ifindex;
    char ifname[IFNAMSIZ];
    time_t timestamp;
    int valid;
};

// Global state with better organization
static struct interface_cache_entry device_cache[MAX_NETDEVICES] = {{0}};
static struct mnl_socket *nl = NULL;
static int shutdown_requested = 0;
static char *recv_buffer = NULL;
static size_t recv_buffer_size = DEFAULT_BUFFER_SIZE;

// Function declarations
char *enhanced_if_indextoname(unsigned int ifindex, char *ifname);
void cleanup_enhanced(void);
void signal_handler(int sig);
static int parse_attr_cb(const struct nlattr *attr, void *data);
static int log_cb(const struct nlmsghdr *nlh, void *data);

// Enhanced interface name resolution with caching and proper error handling
char *enhanced_if_indextoname(unsigned int ifindex, char *ifname) {
    if (ifindex == 0 || ifindex >= MAX_NETDEVICES || !ifname) {
        return NULL;
    }
    
    time_t now = time(NULL);
    struct interface_cache_entry *entry = &device_cache[ifindex];
    
    // Check cache validity
    if (entry->valid && entry->ifindex == ifindex && 
        (now - entry->timestamp) < INTERFACE_CACHE_TTL) {
        strncpy(ifname, entry->ifname, IFNAMSIZ - 1);
        ifname[IFNAMSIZ - 1] = '\0';
        return ifname;
    }
    
    // Cache miss or expired - lookup interface name
    struct ifreq ifr;
    int fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) {
        return NULL;
    }
    
    memset(&ifr, 0, sizeof(ifr));
    ifr.ifr_ifindex = ifindex;
    int status = ioctl(fd, SIOCGIFNAME, &ifr);
    close(fd);
    
    if (status < 0) {
        if (errno == ENODEV) {
            errno = ENXIO;
        }
        return NULL;
    }
    
    // Update cache with proper bounds checking
    entry->ifindex = ifindex;
    strncpy(entry->ifname, ifr.ifr_name, IFNAMSIZ - 1);
    entry->ifname[IFNAMSIZ - 1] = '\0';
    entry->timestamp = now;
    entry->valid = 1;
    
    // Return the cached name
    strncpy(ifname, entry->ifname, IFNAMSIZ - 1);
    ifname[IFNAMSIZ - 1] = '\0';
    return ifname;
}

// Original function maintained for compatibility
char *netlog_if_indextoname(unsigned int ifindex, char *ifname) {
    return enhanced_if_indextoname(ifindex, ifname);
}

// Enhanced cleanup function
void cleanup_enhanced(void) {
    shutdown_requested = 1;
    
    if (nl) {
        mnl_socket_close(nl);
        nl = NULL;
    }
    
    if (recv_buffer) {
        free(recv_buffer);
        recv_buffer = NULL;
    }
    
    // Clear interface cache
    memset(device_cache, 0, sizeof(device_cache));
    
    fprintf(stderr, "Enhanced NFLOG shutdown complete\n");
}

// Original cleanup function maintained for compatibility
void cleanup(void) {
    cleanup_enhanced();
}

// Free net devices function maintained for compatibility  
void free_net_devices(void) {
    // In enhanced version, we use static cache, so just clear it
    memset(device_cache, 0, sizeof(device_cache));
}

// Signal handler for graceful shutdown
void signal_handler(int sig) {
    fprintf(stderr, "Received signal %d, shutting down gracefully...\n", sig);
    shutdown_requested = 1;
}

// Enhanced attribute parsing with better error handling
static int parse_attr_cb(const struct nlattr *attr, void *data) {
    const struct nlattr **tb = data;
    int type = mnl_attr_get_type(attr);

    /* skip unsupported attribute in user-space */
    if (mnl_attr_type_valid(attr, NFULA_MAX) < 0) {
        return MNL_CB_OK;
    }

    switch(type) {
        case NFULA_HWTYPE:
            if (mnl_attr_validate(attr, MNL_TYPE_U16) < 0) {
                perror("mnl_attr_validate HWTYPE");
                return MNL_CB_ERROR;
            }
            break;
        case NFULA_MARK:
        case NFULA_IFINDEX_INDEV:
        case NFULA_IFINDEX_OUTDEV:
        case NFULA_IFINDEX_PHYSINDEV:
        case NFULA_IFINDEX_PHYSOUTDEV:
            if (mnl_attr_validate(attr, MNL_TYPE_U32) < 0) {
                perror("mnl_attr_validate U32");
                return MNL_CB_ERROR;
            }
            break;
        case NFULA_TIMESTAMP:
            if (mnl_attr_validate2(attr, MNL_TYPE_UNSPEC,
                        sizeof(struct nfulnl_msg_packet_timestamp)) < 0) {
                perror("mnl_attr_validate TIMESTAMP");
                return MNL_CB_ERROR;
            }
            break;
        case NFULA_HWADDR:
            if (mnl_attr_validate2(attr, MNL_TYPE_UNSPEC,
                        sizeof(struct nfulnl_msg_packet_hw)) < 0) {
                perror("mnl_attr_validate HWADDR");
                return MNL_CB_ERROR;
            }
            break;
        case NFULA_PACKET_HDR:
            if (mnl_attr_validate2(attr, MNL_TYPE_UNSPEC,
                        sizeof(struct nfulnl_msg_packet_hdr)) < 0) {
                perror("mnl_attr_validate PACKET_HDR");
                return MNL_CB_ERROR;
            }
            break;
        case NFULA_PREFIX:
            if (mnl_attr_validate(attr, MNL_TYPE_NUL_STRING) < 0) {
                perror("mnl_attr_validate PREFIX");
                return MNL_CB_ERROR;
            }
            break;
        case NFULA_PAYLOAD:
            // Payload doesn't need validation
            break;
        default:
            // Unknown attribute type, skip
            break;
    }
    
    tb[type] = attr;
    return MNL_CB_OK;
}

// Enhanced log callback with improved error handling and memory safety
static int log_cb(const struct nlmsghdr *nlh, void *data) {
    struct nlattr *tb[NFULA_MAX+1] = {};
    char ifname_buf[IFNAMSIZ];

    if (!nlh) {
        return MNL_CB_ERROR;
    }

    if (mnl_attr_parse(nlh, sizeof(struct nfgenmsg), parse_attr_cb, tb) < 0) {
        return MNL_CB_ERROR;
    }

    // Print prefix if available
    if (tb[NFULA_PREFIX]) {
        const char *prefix = mnl_attr_get_str(tb[NFULA_PREFIX]);
        if (prefix) {
            printf("%s ", prefix);
        }
    }

    // Handle input interface
    if (tb[NFULA_IFINDEX_INDEV]) {
        uint32_t indev = ntohl(mnl_attr_get_u32(tb[NFULA_IFINDEX_INDEV]));
        char *instr = enhanced_if_indextoname(indev, ifname_buf);
        printf("IN=%s ", instr ? instr : "");
    } else {
        printf("IN= ");
    }

    // Handle output interface
    if (tb[NFULA_IFINDEX_OUTDEV]) {
        uint32_t outdev = ntohl(mnl_attr_get_u32(tb[NFULA_IFINDEX_OUTDEV]));
        char *outstr = enhanced_if_indextoname(outdev, ifname_buf);
        printf("OUT=%s ", outstr ? outstr : "");
    } else {
        printf("OUT= ");
    }

    // Process packet payload
    uint16_t hwProtocol = 0;
    if (tb[NFULA_PACKET_HDR]) {
        struct nfulnl_msg_packet_hdr* pktHdr = 
            (struct nfulnl_msg_packet_hdr*)mnl_attr_get_payload(tb[NFULA_PACKET_HDR]);
        if (pktHdr) {
            hwProtocol = ntohs(pktHdr->hw_protocol);
        }
    }

    if (tb[NFULA_PAYLOAD]) {
        switch (hwProtocol) {
            case ETH_P_IP: {
                struct iphdr *iph = (struct iphdr *) mnl_attr_get_payload(tb[NFULA_PAYLOAD]);
                if (iph && mnl_attr_get_payload_len(tb[NFULA_PAYLOAD]) >= sizeof(struct iphdr)) {
                    char addressStr[INET_ADDRSTRLEN];
                    
                    // Source address
                    if (inet_ntop(AF_INET, &iph->saddr, addressStr, sizeof(addressStr))) {
                        printf("SRC=%s ", addressStr);
                    }
                    
                    // Destination address
                    if (inet_ntop(AF_INET, &iph->daddr, addressStr, sizeof(addressStr))) {
                        printf("DST=%s ", addressStr);
                    }

                    printf("LEN=%u ", ntohs(iph->tot_len));

                    // Protocol-specific processing with bounds checking
                    int header_len = iph->ihl * 4;
                    if (header_len >= sizeof(struct iphdr) && 
                        mnl_attr_get_payload_len(tb[NFULA_PAYLOAD]) >= header_len) {
                        
                        switch(iph->protocol) {
                            case IPPROTO_TCP: {
                                if (mnl_attr_get_payload_len(tb[NFULA_PAYLOAD]) >= 
                                    header_len + sizeof(struct tcphdr)) {
                                    struct tcphdr *th = (struct tcphdr *) 
                                        ((uint8_t*)iph + header_len);
                                    printf("PROTO=TCP SPT=%u DPT=%u ",
                                            ntohs(th->source), ntohs(th->dest));
                                } else {
                                    printf("PROTO=TCP ");
                                }
                                break;
                            }
                            case IPPROTO_UDP: {
                                if (mnl_attr_get_payload_len(tb[NFULA_PAYLOAD]) >= 
                                    header_len + sizeof(struct udphdr)) {
                                    struct udphdr *uh = (struct udphdr *) 
                                        ((uint8_t*)iph + header_len);
                                    printf("PROTO=UDP SPT=%u DPT=%u LEN=%u ",
                                            ntohs(uh->source), ntohs(uh->dest), ntohs(uh->len));
                                } else {
                                    printf("PROTO=UDP ");
                                }
                                break;
                            }
                            case IPPROTO_ICMP: {
                                if (mnl_attr_get_payload_len(tb[NFULA_PAYLOAD]) >= 
                                    header_len + sizeof(struct icmphdr)) {
                                    struct icmphdr *ich = (struct icmphdr *) 
                                        ((uint8_t*)iph + header_len);
                                    printf("PROTO=ICMP TYPE=%u CODE=%u ",
                                            ich->type, ich->code);
                                } else {
                                    printf("PROTO=ICMP ");
                                }
                                break;
                            }
                            default:
                                printf("PROTO=%u ", iph->protocol);
                        }
                    } else {
                        printf("PROTO=%u ", iph->protocol);
                    }
                }
                break;
            }
            case ETH_P_IPV6: {
                struct ipv6hdr *iph = (struct ipv6hdr *) mnl_attr_get_payload(tb[NFULA_PAYLOAD]);
                if (iph && mnl_attr_get_payload_len(tb[NFULA_PAYLOAD]) >= sizeof(struct ipv6hdr)) {
                    char addressStr[INET6_ADDRSTRLEN];
                    
                    // Source address
                    if (inet_ntop(AF_INET6, &iph->saddr, addressStr, sizeof(addressStr))) {
                        printf("SRC=%s ", addressStr);
                    }
                    
                    // Destination address
                    if (inet_ntop(AF_INET6, &iph->daddr, addressStr, sizeof(addressStr))) {
                        printf("DST=%s ", addressStr);
                    }

                    // Protocol-specific processing with bounds checking
                    if (mnl_attr_get_payload_len(tb[NFULA_PAYLOAD]) >= 
                        sizeof(struct ipv6hdr) + 4) {  // At least IPv6 header + some payload
                        
                        switch (iph->nexthdr) {
                            case IPPROTO_TCP: {
                                if (mnl_attr_get_payload_len(tb[NFULA_PAYLOAD]) >= 
                                    sizeof(struct ipv6hdr) + sizeof(struct tcphdr)) {
                                    struct tcphdr *th = (struct tcphdr *) 
                                        ((uint8_t*) iph + sizeof(*iph));
                                    printf("PROTO=TCP SPT=%u DPT=%u ",
                                            ntohs(th->source), ntohs(th->dest));
                                } else {
                                    printf("PROTO=TCP ");
                                }
                                break;
                            }
                            case IPPROTO_UDP: {
                                if (mnl_attr_get_payload_len(tb[NFULA_PAYLOAD]) >= 
                                    sizeof(struct ipv6hdr) + sizeof(struct udphdr)) {
                                    struct udphdr *uh = (struct udphdr *) 
                                        ((uint8_t *) iph + sizeof(*iph));
                                    printf("PROTO=UDP SPT=%u DPT=%u LEN=%u ",
                                            ntohs(uh->source), ntohs(uh->dest), ntohs(uh->len));
                                } else {
                                    printf("PROTO=UDP ");
                                }
                                break;
                            }
                            case IPPROTO_ICMPV6: {
                                if (mnl_attr_get_payload_len(tb[NFULA_PAYLOAD]) >= 
                                    sizeof(struct ipv6hdr) + sizeof(struct icmp6hdr)) {
                                    struct icmp6hdr *icmpv6h = (struct icmp6hdr *) 
                                        ((uint8_t *) iph + sizeof(*iph));
                                    printf("PROTO=ICMP6 TYPE=%u CODE=%u ", 
                                            icmpv6h->icmp6_type, icmpv6h->icmp6_code);
                                } else {
                                    printf("PROTO=ICMP6 ");
                                }
                                break;
                            }
                            default:
                                printf("PROTO=%d ", iph->nexthdr);
                        }
                    } else {
                        printf("PROTO=%d ", iph->nexthdr);
                    }
                }
                break;
            }
            default:
                // Unknown or unsupported protocol
                break;
        }
    }

    // UID information
    if (tb[NFULA_UID]) {
        uint32_t uid = ntohl(mnl_attr_get_u32(tb[NFULA_UID]));
        printf("UID=%u ", uid);
    }

    // End the log line
    puts("");
    fflush(stdout);

    return MNL_CB_OK;
}

// Configuration helper functions (enhanced with error checking)
static struct nlmsghdr *nflog_build_cfg_pf_request(char *buf, uint8_t command) {
    if (!buf) return NULL;
    
    struct nlmsghdr *nlh = mnl_nlmsg_put_header(buf);
    nlh->nlmsg_type = (NFNL_SUBSYS_ULOG << 8) | NFULNL_MSG_CONFIG;
    nlh->nlmsg_flags = NLM_F_REQUEST;

    struct nfgenmsg *nfg = mnl_nlmsg_put_extra_header(nlh, sizeof(*nfg));
    nfg->nfgen_family = AF_INET;
    nfg->version = NFNETLINK_V0;

    struct nfulnl_msg_config_cmd cmd = {
        .command = command,
    };
    mnl_attr_put(nlh, NFULA_CFG_CMD, sizeof(cmd), &cmd);

    return nlh;
}

static struct nlmsghdr *nflog_build_cfg_request(char *buf, uint8_t command, int qnum) {
    if (!buf || qnum < 0 || qnum > 65535) return NULL;
    
    struct nlmsghdr *nlh = mnl_nlmsg_put_header(buf);
    nlh->nlmsg_type = (NFNL_SUBSYS_ULOG << 8) | NFULNL_MSG_CONFIG;
    nlh->nlmsg_flags = NLM_F_REQUEST;

    struct nfgenmsg *nfg = mnl_nlmsg_put_extra_header(nlh, sizeof(*nfg));
    nfg->nfgen_family = AF_INET;
    nfg->version = NFNETLINK_V0;
    nfg->res_id = htons(qnum);

    struct nfulnl_msg_config_cmd cmd = {
        .command = command,
    };
    mnl_attr_put(nlh, NFULA_CFG_CMD, sizeof(cmd), &cmd);

    return nlh;
}

static struct nlmsghdr *nflog_build_cfg_params(char *buf, uint8_t mode, int range, int qnum) {
    if (!buf || qnum < 0 || qnum > 65535 || range < 0) return NULL;
    
    struct nlmsghdr *nlh = mnl_nlmsg_put_header(buf);
    nlh->nlmsg_type = (NFNL_SUBSYS_ULOG << 8) | NFULNL_MSG_CONFIG;
    nlh->nlmsg_flags = NLM_F_REQUEST;

    struct nfgenmsg *nfg = mnl_nlmsg_put_extra_header(nlh, sizeof(*nfg));
    nfg->nfgen_family = AF_UNSPEC;
    nfg->version = NFNETLINK_V0;
    nfg->res_id = htons(qnum);

    struct nfulnl_msg_config_mode params = {
        .copy_range = htonl(range),
        .copy_mode = mode,
    };
    mnl_attr_put(nlh, NFULA_CFG_MODE, sizeof(params), &params);

    return nlh;
}

// Enhanced main function with better error handling and resource management
int main(int argc, char *argv[]) {
    char buf[MNL_SOCKET_BUFFER_SIZE];
    struct nlmsghdr *nlh;
    int ret, nfds, sock_fd, stdin_fd;
    unsigned int portid, qnum;
    
    // Install signal handlers for graceful shutdown
    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);
    signal(SIGPIPE, SIG_IGN);  // Ignore broken pipe
    
    atexit(cleanup_enhanced);

    // Validate command line arguments
    if (argc != 2) {
        fprintf(stderr, "Usage: %s [queue_num]\n", argv[0]);
        fprintf(stderr, "Enhanced NFLOG implementation with improved reliability\n");
        exit(EXIT_FAILURE);
    }
    
    // Parse and validate queue number
    char *endptr;
    long qnum_long = strtol(argv[1], &endptr, 10);
    if (*endptr != '\0' || qnum_long < 0 || qnum_long > 65535) {
        fprintf(stderr, "Error: Invalid queue number. Must be 0-65535\n");
        exit(EXIT_FAILURE);
    }
    qnum = (unsigned int)qnum_long;

    // Allocate enhanced receive buffer
    recv_buffer = malloc(recv_buffer_size);
    if (!recv_buffer) {
        perror("Failed to allocate receive buffer");
        exit(EXIT_FAILURE);
    }

    // Initialize netlink socket
    nl = mnl_socket_open(NETLINK_NETFILTER);
    if (nl == NULL) {
        perror("mnl_socket_open");
        exit(EXIT_FAILURE);
    }

    if (mnl_socket_bind(nl, 0, MNL_SOCKET_AUTOPID) < 0) {
        perror("mnl_socket_bind");
        exit(EXIT_FAILURE);
    }
    portid = mnl_socket_get_portid(nl);

    // Set socket buffer size for better performance
    int sock_buffer_size = recv_buffer_size * 2;
    if (setsockopt(mnl_socket_get_fd(nl), SOL_SOCKET, SO_RCVBUF, 
                   &sock_buffer_size, sizeof(sock_buffer_size)) < 0) {
        perror("Warning: setsockopt SO_RCVBUF failed");
        // Continue anyway - not fatal
    }

    // Configure NFLOG with enhanced error checking
    nlh = nflog_build_cfg_pf_request(buf, NFULNL_CFG_CMD_PF_UNBIND);
    if (!nlh) {
        fprintf(stderr, "Error: Failed to build PF unbind request\n");
        exit(EXIT_FAILURE);
    }
    if (mnl_socket_sendto(nl, nlh, nlh->nlmsg_len) < 0) {
        perror("mnl_socket_send PF unbind");
        exit(EXIT_FAILURE);
    }

    nlh = nflog_build_cfg_pf_request(buf, NFULNL_CFG_CMD_PF_BIND);
    if (!nlh) {
        fprintf(stderr, "Error: Failed to build PF bind request\n");
        exit(EXIT_FAILURE);
    }
    if (mnl_socket_sendto(nl, nlh, nlh->nlmsg_len) < 0) {
        perror("mnl_socket_send PF bind");
        exit(EXIT_FAILURE);
    }

    nlh = nflog_build_cfg_request(buf, NFULNL_CFG_CMD_BIND, qnum);
    if (!nlh) {
        fprintf(stderr, "Error: Failed to build queue bind request\n");
        exit(EXIT_FAILURE);
    }
    if (mnl_socket_sendto(nl, nlh, nlh->nlmsg_len) < 0) {
        perror("mnl_socket_send queue bind");
        exit(EXIT_FAILURE);
    }

    nlh = nflog_build_cfg_params(buf, NFULNL_COPY_PACKET, 0xFFFF, qnum);
    if (!nlh) {
        fprintf(stderr, "Error: Failed to build config params request\n");
        exit(EXIT_FAILURE);
    }
    if (mnl_socket_sendto(nl, nlh, nlh->nlmsg_len) < 0) {
        perror("mnl_socket_send config params");
        exit(EXIT_FAILURE);
    }

    sock_fd = mnl_socket_get_fd(nl);
    stdin_fd = fileno(stdin);
    nfds = (sock_fd > stdin_fd ? sock_fd : stdin_fd) + 1;

    if (fcntl(sock_fd,  F_SETFL, O_NONBLOCK) < 0 ||
        fcntl(stdin_fd, F_SETFL, O_NONBLOCK) < 0) {
        perror("fcntl");
        exit(EXIT_FAILURE);
    }

    fprintf(stderr, "Enhanced NFLOG started on queue %u\n", qnum);

    // Enhanced main loop with better error handling
    while (!shutdown_requested) {
        fd_set fds;
        char c;
        int select_result;

        FD_ZERO(&fds);
        FD_SET(sock_fd, &fds);
        FD_SET(stdin_fd, &fds);

        select_result = select(nfds, &fds, NULL, NULL, NULL);
        
        if (select_result < 0) {
            if (errno == EINTR) {
                // Interrupted by signal, check shutdown flag
                continue;
            }
            perror("select");
            break;
        }

        if (FD_ISSET(stdin_fd, &fds)) {
            // Check for stdin closure or input
            if (read(stdin_fd, &c, 1) <= 0) {
                fprintf(stderr, "stdin closed, shutting down...\n");
                break;
            }
        }

        if (!FD_ISSET(sock_fd, &fds)) {
            // No data on socket
            continue;
        }

        ret = mnl_socket_recvfrom(nl, recv_buffer, recv_buffer_size);
        if (ret == -1) {
            if (errno == ENOSPC || errno == ENOBUFS) {
                // Buffer overrun - usually recoverable
                fprintf(stderr, "Warning: Buffer overrun, some packets may be lost\n");
                continue;
            } else if (errno == EAGAIN || errno == EWOULDBLOCK) {
                // No data available - normal with O_NONBLOCK
                continue;
            } else {
                perror("mnl_socket_recvfrom");
                break;
            }
        }

        if (ret == 0) {
            fprintf(stderr, "Socket closed by remote end\n");
            break;
        }

        if (mnl_cb_run(recv_buffer, ret, 0, portid, log_cb, NULL) < 0) {
            perror("mnl_cb_run");
            // Don't exit on callback errors - try to continue
            continue;
        }
    }

    fprintf(stderr, "Enhanced NFLOG shutting down gracefully\n");
    return 0;
}