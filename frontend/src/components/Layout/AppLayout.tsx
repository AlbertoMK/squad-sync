import type { ReactNode } from 'react';
import { AppShell, Burger, Group, Title, Avatar, Menu, ActionIcon } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconLogout, IconUser, IconCalendar, IconDeviceGamepad2, IconHome } from '@tabler/icons-react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';

interface AppLayoutProps {
    children: ReactNode;
}

export default function AppLayout({ children }: AppLayoutProps) {
    const [opened, { toggle }] = useDisclosure();
    const { user, logout } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();

    const handleLogout = () => {
        logout();
        navigate('/login');
    };

    const navItems = [
        { icon: IconHome, label: 'Dashboard', path: '/' },
        { icon: IconDeviceGamepad2, label: 'Juegos', path: '/games' },
        { icon: IconCalendar, label: 'Disponibilidad', path: '/availability' },
    ];

    return (
        <AppShell
            header={{ height: 60 }}
            navbar={{ width: 300, breakpoint: 'sm', collapsed: { mobile: !opened } }}
            padding="md"
        >
            <AppShell.Header>
                <Group h="100%" px="md" justify="space-between">
                    <Group>
                        <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" />
                        <Title order={3}>🎮 SquadSync</Title>
                    </Group>

                    <Menu shadow="md" width={200}>
                        <Menu.Target>
                            <ActionIcon variant="subtle" size="lg">
                                <Avatar color={user?.avatarColor} radius="xl" size="sm">
                                    {user?.username.charAt(0).toUpperCase()}
                                </Avatar>
                            </ActionIcon>
                        </Menu.Target>

                        <Menu.Dropdown>
                            <Menu.Label>{user?.username}</Menu.Label>
                            <Menu.Item
                                leftSection={<IconUser size={14} />}
                                onClick={() => navigate('/profile')}
                            >
                                Perfil
                            </Menu.Item>
                            <Menu.Divider />
                            <Menu.Item
                                color="red"
                                leftSection={<IconLogout size={14} />}
                                onClick={handleLogout}
                            >
                                Cerrar sesión
                            </Menu.Item>
                        </Menu.Dropdown>
                    </Menu>
                </Group>
            </AppShell.Header>

            <AppShell.Navbar p="md">
                {navItems.map((item) => {
                    const Icon = item.icon;
                    const isActive = location.pathname === item.path;

                    return (
                        <Group
                            key={item.path}
                            onClick={() => navigate(item.path)}
                            style={{
                                padding: '12px 16px',
                                borderRadius: '8px',
                                cursor: 'pointer',
                                backgroundColor: isActive ? 'var(--mantine-color-blue-filled)' : 'transparent',
                                marginBottom: '4px',
                            }}
                        >
                            <Icon size={20} />
                            <span>{item.label}</span>
                        </Group>
                    );
                })}
            </AppShell.Navbar>

            <AppShell.Main>{children}</AppShell.Main>
        </AppShell>
    );
}
